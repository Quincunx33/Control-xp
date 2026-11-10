"""NEXUS REMOTE Limbo Application setup, routes, and WebSocket dispatcher."""
from __future__ import annotations
import json
import logging
import os
from typing import Any

from limbo.app import Limbo
from limbo.http import Request, Response
from limbo.middleware import cors

from .config import BASE_DIR
from .database.migrations.runner import run_migrations
from .security.bot_guard_integration import bot_guard_middleware
from .auth.middleware import require_auth
from .auth.service import register_user, authenticate_user, revoke_session, get_user_sessions
from .devices.registry import (
    register_device, list_devices, get_device, rename_device,
    revoke_device, delete_device, update_device_status
)
from .pairing.service import (
    create_pairing_request, verify_pairing_code, approve_pairing,
    reject_pairing, get_pairing_status
)
from .commands.service import (
    create_command, list_device_commands, get_command_status, cancel_command
)
from .commands.registry import COMMAND_REGISTRY
from .files.service import (
    create_file_transfer, save_uploaded_file, get_file_transfer, list_device_transfers
)
from .audit.service import get_recent_audit_logs, get_recent_security_events
from .websocket.gateway import gateway

logger = logging.getLogger("nexus.app")

def create_app() -> Limbo:
    # Run database schema migrations
    run_migrations()

    app = Limbo(name="NexusRemote")

    # 1. CORS Middleware
    app.middleware(cors(
        allow_origins=["*"],
        allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
        allow_headers=["*"],
        expose_headers=["X-Bot-Guard-Score", "Retry-After"]
    ))

    # 2. Bot Guard Pre-Auth Security Middleware
    app.middleware(bot_guard_middleware)

    # --- HEALTH CHECK ---
    @app.get("/api/v1/health")
    def health_check(request: Request) -> Response:
        return Response.json({
            "status": "healthy",
            "service": "NEXUS REMOTE",
            "version": "1.0.0",
            "bot_guard": "active"
        })

    # --- AUTHENTICATION ENDPOINTS ---
    @app.post("/api/v1/auth/register")
    def api_register(request: Request) -> Response:
        try:
            body = request.json or {}
            res = register_user(
                email=body.get("email", ""),
                password=body.get("password", ""),
                name=body.get("name", ""),
                client_ip=request.state.get("client_ip")
            )
            return Response.json({"user": res, "message": "User registered successfully"}, status=201)
        except ValueError as e:
            return Response.json({"error": str(e), "code": "VALIDATION_ERROR"}, status=400)

    @app.post("/api/v1/auth/login")
    def api_login(request: Request) -> Response:
        try:
            body = request.json or {}
            res = authenticate_user(
                email=body.get("email", ""),
                password=body.get("password", ""),
                device_info=body.get("device_info", request.headers.get("user-agent", "Unknown Client")),
                client_ip=request.state.get("client_ip")
            )
            return Response.json(res)
        except ValueError as e:
            return Response.json({"error": str(e), "code": "AUTH_FAILED"}, status=401)

    @app.post("/api/v1/auth/logout")
    def api_logout(request: Request) -> Response:
        auth_header = request.headers.get("authorization", "")
        token = auth_header[7:].strip() if auth_header.startswith("Bearer ") else ""
        if token:
            revoke_session(token, client_ip=request.state.get("client_ip"))
        return Response.json({"message": "Logged out successfully"})

    @app.get("/api/v1/auth/me")
    def api_me(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        return Response.json({"user": user})

    @app.get("/api/v1/auth/sessions")
    def api_sessions(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        sessions = get_user_sessions(user["id"])
        return Response.json({"sessions": sessions})

    # --- DEVICE ENDPOINTS ---
    @app.get("/api/v1/devices")
    def api_list_devices(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        devices = list_devices(user["id"])
        return Response.json({"devices": devices})

    @app.post("/api/v1/devices/register")
    def api_register_device(request: Request) -> Response:
        # Can be registered by unauthenticated device (to pair later) or authenticated user
        user, _ = require_auth(request)
        body = request.json or {}
        try:
            res = register_device(
                name=body.get("name", "Android Device"),
                model=body.get("model", "Unknown Model"),
                android_version=body.get("android_version", "Android 14"),
                app_version=body.get("app_version", "1.0.0"),
                screen_res=body.get("screen_res", "1080x2400"),
                user_id=user["id"] if user else None,
                client_ip=request.state.get("client_ip")
            )
            return Response.json(res, status=201)
        except ValueError as e:
            return Response.json({"error": str(e), "code": "REGISTRATION_FAILED"}, status=400)

    @app.get("/api/v1/devices/{id}")
    def api_get_device(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        dev_id = request.path_params.get("id", "")
        dev = get_device(dev_id, user_id=user["id"])
        if not dev:
            return Response.json({"error": "Device not found", "code": "NOT_FOUND"}, status=404)
        return Response.json({"device": dev})

    @app.patch("/api/v1/devices/{id}")
    def api_patch_device(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        dev_id = request.path_params.get("id", "")
        body = request.json or {}
        new_name = body.get("name")
        if new_name:
            try:
                rename_device(dev_id, user["id"], new_name)
                return Response.json({"message": "Device renamed successfully", "name": new_name})
            except ValueError as e:
                return Response.json({"error": str(e)}, status=400)
        return Response.json({"error": "No valid fields to update"}, status=400)

    @app.delete("/api/v1/devices/{id}")
    def api_delete_device(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        dev_id = request.path_params.get("id", "")
        success = delete_device(dev_id, user["id"], client_ip=request.state.get("client_ip"))
        if not success:
            return Response.json({"error": "Device not found or not owned"}, status=404)
        return Response.json({"message": "Device deleted"})

    @app.post("/api/v1/devices/{id}/revoke")
    def api_revoke_device(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        dev_id = request.path_params.get("id", "")
        success = revoke_device(dev_id, user["id"], client_ip=request.state.get("client_ip"))
        if not success:
            return Response.json({"error": "Device not found or cannot be revoked"}, status=404)
        return Response.json({"message": "Device access revoked"})

    @app.get("/api/v1/devices/{id}/status")
    def api_device_status(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        dev_id = request.path_params.get("id", "")
        dev = get_device(dev_id, user_id=user["id"])
        if not dev:
            return Response.json({"error": "Device not found"}, status=404)
        return Response.json({"status": dev["status"], "telemetry": dev.get("telemetry", {}), "last_seen": dev.get("last_seen")})

    # --- PAIRING ENDPOINTS ---
    @app.post("/api/v1/pairing/create")
    def api_pairing_create(request: Request) -> Response:
        body = request.json or {}
        dev_id = body.get("device_id")
        if not dev_id:
            return Response.json({"error": "device_id is required"}, status=400)
        try:
            res = create_pairing_request(
                device_id=dev_id,
                device_info=body.get("device_info"),
                client_ip=request.state.get("client_ip")
            )
            return Response.json(res, status=201)
        except ValueError as e:
            return Response.json({"error": str(e)}, status=400)

    @app.post("/api/v1/pairing/verify")
    def api_pairing_verify(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        body = request.json or {}
        code = body.get("code", "")
        try:
            res = verify_pairing_code(code, user_id=user["id"], client_ip=request.state.get("client_ip"))
            return Response.json(res)
        except ValueError as e:
            return Response.json({"error": str(e)}, status=400)

    @app.post("/api/v1/pairing/approve")
    def api_pairing_approve(request: Request) -> Response:
        body = request.json or {}
        pairing_id = body.get("pairing_id")
        dev_id = body.get("device_id")
        if not pairing_id or not dev_id:
            return Response.json({"error": "pairing_id and device_id are required"}, status=400)
        try:
            res = approve_pairing(pairing_id, dev_id, client_ip=request.state.get("client_ip"))
            return Response.json(res)
        except ValueError as e:
            return Response.json({"error": str(e)}, status=400)

    @app.post("/api/v1/pairing/reject")
    def api_pairing_reject(request: Request) -> Response:
        body = request.json or {}
        pairing_id = body.get("pairing_id")
        dev_id = body.get("device_id")
        if not pairing_id or not dev_id:
            return Response.json({"error": "pairing_id and device_id are required"}, status=400)
        success = reject_pairing(pairing_id, dev_id, client_ip=request.state.get("client_ip"))
        return Response.json({"rejected": success})

    @app.get("/api/v1/pairing/{id}")
    def api_pairing_status(request: Request) -> Response:
        p_id = request.path_params.get("id", "")
        req = get_pairing_status(p_id)
        if not req:
            return Response.json({"error": "Pairing request not found"}, status=404)
        return Response.json({"pairing": req})

    # --- COMMAND ENDPOINTS ---
    @app.get("/api/v1/commands/registry")
    def api_command_registry(request: Request) -> Response:
        cmds = [{
            "type": c.command_type,
            "description": c.description,
            "permissions": list(c.required_permissions),
            "timeout_seconds": c.timeout_seconds,
            "parameters": c.param_schema
        } for c in COMMAND_REGISTRY.values()]
        return Response.json({"commands": cmds})

    @app.get("/api/v1/devices/{id}/commands")
    def api_list_device_commands(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        dev_id = request.path_params.get("id", "")
        commands = list_device_commands(dev_id, user["id"])
        return Response.json({"commands": commands})

    @app.post("/api/v1/devices/{id}/commands")
    def api_create_command(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        dev_id = request.path_params.get("id", "")
        body = request.json or {}
        cmd_type = body.get("command_type", "")
        params = body.get("params", {})
        try:
            res = create_command(
                device_id=dev_id,
                user_id=user["id"],
                command_type=cmd_type,
                params=params,
                client_ip=request.state.get("client_ip")
            )
            return Response.json(res, status=201)
        except ValueError as e:
            return Response.json({"error": str(e)}, status=400)

    @app.get("/api/v1/commands/{id}")
    def api_get_command(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        cmd_id = request.path_params.get("id", "")
        cmd = get_command_status(cmd_id, user["id"])
        if not cmd:
            return Response.json({"error": "Command not found"}, status=404)
        return Response.json({"command": cmd})

    @app.post("/api/v1/commands/{id}/cancel")
    def api_cancel_command(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        cmd_id = request.path_params.get("id", "")
        success = cancel_command(cmd_id, user["id"])
        return Response.json({"cancelled": success})

    # --- FILE TRANSFERS ---
    @app.post("/api/v1/files/create")
    def api_file_create(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        body = request.json or {}
        dev_id = body.get("device_id")
        filename = body.get("filename")
        direction = body.get("direction", "UPLOAD")
        filesize = body.get("filesize", 0)
        checksum = body.get("checksum")
        if not dev_id or not filename:
            return Response.json({"error": "device_id and filename are required"}, status=400)
        try:
            res = create_file_transfer(dev_id, user["id"], filename, direction, filesize, checksum)
            return Response.json(res, status=201)
        except ValueError as e:
            return Response.json({"error": str(e)}, status=400)

    @app.post("/api/v1/files/upload/{id}")
    def api_file_upload(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        t_id = request.path_params.get("id", "")
        content = request.body
        if not content:
            return Response.json({"error": "Empty file content"}, status=400)
        try:
            path = save_uploaded_file(t_id, content, user["id"])
            return Response.json({"message": "File uploaded successfully", "path": path})
        except ValueError as e:
            return Response.json({"error": str(e)}, status=400)

    @app.get("/api/v1/files/device/{id}")
    def api_device_files(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        dev_id = request.path_params.get("id", "")
        files = list_device_transfers(dev_id, user["id"])
        return Response.json({"transfers": files})

    # --- AUDIT AND SECURITY LOGS ---
    @app.get("/api/v1/audit")
    def api_audit(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        logs = get_recent_audit_logs(limit=100, user_id=user["id"])
        return Response.json({"audit_logs": logs})

    @app.get("/api/v1/security/events")
    def api_security_events(request: Request) -> Response:
        user, err = require_auth(request)
        if err:
            return err
        events = get_recent_security_events(limit=100)
        return Response.json({"security_events": events})

    # --- WEBSOCKET ROUTE ---
    @app.websocket("/ws")
    def ws_handler(request: Request, websocket: Any) -> None:
        gateway.register_connection(websocket)
        try:
            while not websocket.closed:
                msg = websocket.receive()
                if msg is None:
                    break
                if isinstance(msg, bytes):
                    msg = msg.decode("utf-8", errors="ignore")
                gateway.handle_message(websocket, msg)
        except Exception as e:
            logger.debug(f"WebSocket closed: {e}")
        finally:
            gateway.unregister_connection(websocket)

    # --- STATIC WEB DASHBOARD ROUTES ---
    web_dir = BASE_DIR.parent / "web"
    if web_dir.is_dir():
        assets_dir = web_dir / "assets"
        if assets_dir.is_dir():
            app.static("/assets", assets_dir)

        def _serve_page(filename: str) -> Callable[[Request], Response]:
            def handler(request: Request) -> Response:
                p = web_dir / filename
                if p.is_file():
                    return Limbo.send_file(p)
                return Response.text(f"Page {filename} not found", status=404)
            return handler

        app.get("/")(_serve_page("index.html"))
        app.get("/login")(_serve_page("login.html"))
        app.get("/register")(_serve_page("register.html"))
        app.get("/dashboard")(_serve_page("dashboard.html"))
        app.get("/devices")(_serve_page("devices.html"))
        app.get("/device-details")(_serve_page("device-details.html"))
        app.get("/sessions")(_serve_page("sessions.html"))
        app.get("/settings")(_serve_page("settings.html"))

    return app

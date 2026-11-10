"""Authenticated WebSocket Gateway for Device Presence, Commands, and WebRTC Signaling."""
from __future__ import annotations
import json
import logging
import threading
import time
from typing import Any, Optional
from ..devices.registry import (
    verify_device_credentials, set_device_connection_state,
    update_device_status, update_device_permissions, get_device
)
from ..auth.service import verify_session
from ..commands.service import record_command_result, set_dispatch_hook
from ..audit.service import log_audit, log_security_event

logger = logging.getLogger("nexus.websocket")

class WebSocketGateway:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        # device_id -> ws
        self.devices: dict[str, Any] = {}
        # user_id -> set of ws
        self.user_clients: dict[str, set[Any]] = {}
        # ws -> client_info
        self.connections: dict[Any, dict[str, Any]] = {}
        # Wire up command dispatcher
        set_dispatch_hook(self.dispatch_command_to_device)

    def register_connection(self, ws: Any) -> None:
        with self.lock:
            self.connections[ws] = {"authenticated": False, "type": None, "id": None}

    def unregister_connection(self, ws: Any) -> None:
        device_id_to_offline = None
        user_id_to_remove = None

        with self.lock:
            info = self.connections.pop(ws, None)
            if info and info.get("authenticated"):
                if info.get("type") == "device":
                    dev_id = info.get("id")
                    if self.devices.get(dev_id) == ws:
                        del self.devices[dev_id]
                        device_id_to_offline = dev_id
                elif info.get("type") == "user":
                    uid = info.get("id")
                    if uid in self.user_clients and ws in self.user_clients[uid]:
                        self.user_clients[uid].remove(ws)
                        if not self.user_clients[uid]:
                            del self.user_clients[uid]

        if device_id_to_offline:
            try:
                set_device_connection_state(device_id_to_offline, "OFFLINE")
                self.broadcast_to_subscribers(device_id_to_offline, {
                    "type": "device_status_changed",
                    "device_id": device_id_to_offline,
                    "status": "OFFLINE",
                    "timestamp": time.time()
                })
            except Exception as e:
                logger.error(f"Error marking device offline: {e}")

    def handle_message(self, ws: Any, raw_message: str) -> None:
        if len(raw_message) > 1024 * 512:  # 512KB max message size
            ws.close(1009, "Message too large")
            return

        try:
            msg = json.loads(raw_message)
        except Exception:
            ws.close(1003, "Invalid JSON")
            return

        msg_type = msg.get("type")
        if not msg_type:
            return

        with self.lock:
            info = self.connections.get(ws)

        if not info:
            return

        # 1. Unauthenticated handling
        if not info.get("authenticated"):
            if msg_type == "auth":
                self._handle_auth(ws, msg)
            else:
                ws.send(json.dumps({"type": "error", "message": "Authentication required"}))
                ws.close(4001, "Unauthorized")
            return

        # 2. Authenticated handlers
        if info["type"] == "device":
            self._handle_device_message(ws, info["id"], msg)
        elif info["type"] == "user":
            self._handle_user_message(ws, info["id"], msg)

    def _handle_auth(self, ws: Any, msg: dict[str, Any]) -> None:
        # Check if device auth
        device_id = msg.get("device_id")
        device_key = msg.get("device_key")
        if device_id and device_key:
            if verify_device_credentials(device_id, device_key):
                with self.lock:
                    self.devices[device_id] = ws
                    self.connections[ws] = {"authenticated": True, "type": "device", "id": device_id}
                set_device_connection_state(device_id, "ONLINE")
                ws.send(json.dumps({"type": "auth_ok", "role": "device", "device_id": device_id}))
                # Broadcast online status
                self.broadcast_to_subscribers(device_id, {
                    "type": "device_status_changed",
                    "device_id": device_id,
                    "status": "ONLINE",
                    "timestamp": time.time()
                })
                return
            else:
                ws.send(json.dumps({"type": "auth_error", "message": "Invalid device credentials"}))
                ws.close(4003, "Forbidden")
                return

        # Check if user auth token
        token = msg.get("token")
        if token:
            session = verify_session(token)
            if session:
                user_id = session["user"]["id"]
                with self.lock:
                    if user_id not in self.user_clients:
                        self.user_clients[user_id] = set()
                    self.user_clients[user_id].add(ws)
                    self.connections[ws] = {"authenticated": True, "type": "user", "id": user_id}
                ws.send(json.dumps({"type": "auth_ok", "role": "user", "user_id": user_id}))
                return

        ws.send(json.dumps({"type": "auth_error", "message": "Valid user session required"}))
        ws.close(4001, "Unauthorized")

    def _handle_device_message(self, ws: Any, device_id: str, msg: dict[str, Any]) -> None:
        msg_type = msg.get("type")

        if msg_type == "heartbeat" or msg_type == "status_update":
            # Telemetry update
            update_device_status(
                device_id=device_id,
                battery_level=msg.get("battery_level"),
                charging=msg.get("charging"),
                battery_temp=msg.get("battery_temp"),
                storage_free_mb=msg.get("storage_free_mb"),
                storage_total_mb=msg.get("storage_total_mb"),
                network_type=msg.get("network_type"),
                wifi_ssid=msg.get("wifi_ssid"),
                latitude=msg.get("latitude"),
                longitude=msg.get("longitude"),
                accuracy=msg.get("accuracy"),
                ram_free_mb=msg.get("ram_free_mb"),
                ram_total_mb=msg.get("ram_total_mb")
            )
            if "permissions" in msg and isinstance(msg["permissions"], dict):
                update_device_permissions(device_id, msg["permissions"])

            ws.send(json.dumps({"type": "heartbeat_ack", "timestamp": time.time()}))

            # Forward to dashboards
            self.broadcast_to_subscribers(device_id, {
                "type": "device_telemetry",
                "device_id": device_id,
                "telemetry": msg,
                "timestamp": time.time()
            })

        elif msg_type == "command_result":
            cmd_id = msg.get("command_id")
            if cmd_id:
                record_command_result(
                    command_id=cmd_id,
                    device_id=device_id,
                    result=msg.get("result"),
                    error=msg.get("error")
                )
                self.broadcast_to_subscribers(device_id, {
                    "type": "command_result",
                    "device_id": device_id,
                    "command_id": cmd_id,
                    "status": "FAILED" if msg.get("error") else "SUCCESS",
                    "result": msg.get("result"),
                    "error": msg.get("error"),
                    "timestamp": time.time()
                })

        elif msg_type in ("webrtc_offer", "webrtc_answer", "webrtc_candidate", "webrtc_stop"):
            # WebRTC signaling from device -> forward to dashboard
            self.broadcast_to_subscribers(device_id, {
                "type": msg_type,
                "device_id": device_id,
                "data": msg.get("data"),
                "timestamp": time.time()
            })

    def _handle_user_message(self, ws: Any, user_id: str, msg: dict[str, Any]) -> None:
        msg_type = msg.get("type")

        if msg_type in ("webrtc_offer", "webrtc_answer", "webrtc_candidate", "webrtc_stop"):
            target_device_id = msg.get("device_id")
            if target_device_id:
                dev = get_device(target_device_id, user_id=user_id)
                if dev:
                    self.send_to_device(target_device_id, {
                        "type": msg_type,
                        "device_id": target_device_id,
                        "data": msg.get("data"),
                        "timestamp": time.time()
                    })

    def dispatch_command_to_device(self, device_id: str, command_payload: dict[str, Any]) -> bool:
        with self.lock:
            ws = self.devices.get(device_id)
        if ws:
            try:
                ws.send(json.dumps(command_payload))
                return True
            except Exception as e:
                logger.error(f"Failed to send command to device {device_id}: {e}")
                return False
        return False

    def send_to_device(self, device_id: str, payload: dict[str, Any]) -> bool:
        with self.lock:
            ws = self.devices.get(device_id)
        if ws:
            try:
                ws.send(json.dumps(payload))
                return True
            except Exception:
                return False
        return False

    def broadcast_to_subscribers(self, device_id: str, payload: dict[str, Any]) -> None:
        device = get_device(device_id)
        owner_id = device.get("user_id") if device else None
        if not owner_id:
            return
        with self.lock:
            subscribers = list(self.user_clients.get(owner_id, set()))

        payload_str = json.dumps(payload)
        for ws in subscribers:
            try:
                ws.send(payload_str)
            except Exception:
                pass

gateway = WebSocketGateway()

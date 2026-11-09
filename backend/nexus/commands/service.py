"""Command execution pipeline and lifecycle management."""
from __future__ import annotations
import json
import secrets
import time
from typing import Any, Optional
from ..database.db import execute, fetch_one, fetch_all, transaction
from ..audit.service import log_audit
from .registry import validate_command

# In-memory hook to websocket gateway to dispatch commands directly
_dispatch_hook: Optional[Any] = None

def set_dispatch_hook(hook: Any) -> None:
    global _dispatch_hook
    _dispatch_hook = hook

def create_command(
    device_id: str,
    user_id: str,
    command_type: str,
    params: Optional[dict[str, Any]] = None,
    client_ip: Optional[str] = None
) -> dict[str, Any]:
    # Validate command definition & params
    defn = validate_command(command_type, params)

    # Validate device ownership
    dev = fetch_one("SELECT id, user_id, status FROM devices WHERE id = ?;", (device_id,))
    if not dev or dev["user_id"] != user_id:
        raise ValueError("Device not found or not owned by user")
    if dev["status"] == "REVOKED":
        raise ValueError("Cannot execute commands on a revoked device")

    cmd_id = f"cmd_{secrets.token_hex(8)}"
    now = time.time()
    params_str = json.dumps(params or {})

    with transaction():
        execute(
            """
            INSERT INTO commands (id, device_id, user_id, command_type, params_json, status, created_at)
            VALUES (?, ?, ?, ?, ?, 'PENDING', ?);
            """,
            (cmd_id, device_id, user_id, command_type, params_str, now)
        )

    log_audit("COMMAND_CREATED", user_id=user_id, device_id=device_id, details={"command_id": cmd_id, "type": command_type}, client_ip=client_ip)

    # Try immediate WebSocket dispatch if device is online
    dispatched = False
    if _dispatch_hook:
        try:
            dispatched = _dispatch_hook(device_id, {
                "type": "command",
                "command_id": cmd_id,
                "command_type": command_type,
                "params": params or {},
                "timestamp": now
            })
            if dispatched:
                with transaction():
                    execute("UPDATE commands SET status = 'DISPATCHED', dispatched_at = ? WHERE id = ?;", (time.time(), cmd_id))
        except Exception:
            pass

    return {
        "command_id": cmd_id,
        "device_id": device_id,
        "command_type": command_type,
        "status": "DISPATCHED" if dispatched else "PENDING",
        "created_at": now
    }

def record_command_result(
    command_id: str,
    device_id: str,
    result: Optional[dict[str, Any]] = None,
    error: Optional[str] = None
) -> bool:
    cmd = fetch_one("SELECT * FROM commands WHERE id = ? AND device_id = ?;", (command_id, device_id))
    if not cmd:
        return False

    now = time.time()
    status = "FAILED" if error else "SUCCESS"
    res_str = json.dumps(result or {})

    with transaction():
        execute(
            """
            INSERT INTO command_results (command_id, device_id, result_json, error_message, received_at)
            VALUES (?, ?, ?, ?, ?);
            """,
            (command_id, device_id, res_str, error, now)
        )
        execute(
            "UPDATE commands SET status = ?, completed_at = ? WHERE id = ?;",
            (status, now, command_id)
        )

    log_audit("COMMAND_RESULT", user_id=cmd["user_id"], device_id=device_id, details={"command_id": command_id, "status": status, "error": error})
    return True

def get_command_status(command_id: str, user_id: str) -> Optional[dict[str, Any]]:
    cmd = fetch_one("SELECT * FROM commands WHERE id = ? AND user_id = ?;", (command_id, user_id))
    if not cmd:
        return None
    res = fetch_one("SELECT * FROM command_results WHERE command_id = ?;", (command_id,))
    if res and res.get("result_json"):
        try:
            cmd["result"] = json.loads(res["result_json"])
        except Exception:
            cmd["result"] = {}
        cmd["error"] = res.get("error_message")
    return cmd

def list_device_commands(device_id: str, user_id: str, limit: int = 50) -> list[dict[str, Any]]:
    rows = fetch_all(
        """
        SELECT c.*, r.result_json, r.error_message
        FROM commands c
        LEFT JOIN command_results r ON c.id = r.command_id
        WHERE c.device_id = ? AND c.user_id = ?
        ORDER BY c.created_at DESC LIMIT ?;
        """,
        (device_id, user_id, limit)
    )
    for r in rows:
        if r.get("params_json"):
            try:
                r["params"] = json.loads(r["params_json"])
            except Exception:
                r["params"] = {}
        if r.get("result_json"):
            try:
                r["result"] = json.loads(r["result_json"])
            except Exception:
                r["result"] = {}
    return rows

def cancel_command(command_id: str, user_id: str) -> bool:
    with transaction():
        cur = execute(
            "UPDATE commands SET status = 'CANCELLED' WHERE id = ? AND user_id = ? AND status IN ('PENDING', 'DISPATCHED');",
            (command_id, user_id)
        )
    return cur.rowcount > 0

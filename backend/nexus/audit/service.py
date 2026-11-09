"""Audit logging and security event monitoring service."""
from __future__ import annotations
import json
import secrets
import time
from typing import Any, Optional
from ..database.db import execute, fetch_all, transaction

def log_audit(
    action: str,
    user_id: Optional[str] = None,
    device_id: Optional[str] = None,
    details: Optional[dict[str, Any]] = None,
    client_ip: Optional[str] = None
) -> str:
    log_id = f"aud_{secrets.token_hex(8)}"
    now = time.time()
    details_str = json.dumps(details or {})
    execute(
        """
        INSERT INTO audit_logs (id, user_id, device_id, action, details_json, client_ip, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?);
        """,
        (log_id, user_id, device_id, action, details_str, client_ip, now)
    )
    return log_id

def log_security_event(
    event_type: str,
    severity: str,
    client_ip: Optional[str] = None,
    user_agent: Optional[str] = None,
    details: Optional[dict[str, Any]] = None,
    action_taken: str = "LOG"
) -> str:
    event_id = f"sec_{secrets.token_hex(8)}"
    now = time.time()
    details_str = json.dumps(details or {})
    execute(
        """
        INSERT INTO security_events (id, event_type, severity, client_ip, user_agent, details_json, action_taken, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?);
        """,
        (event_id, event_type, severity.upper(), client_ip, user_agent, details_str, action_taken, now)
    )
    return event_id

def get_recent_audit_logs(limit: int = 50, user_id: Optional[str] = None) -> list[dict]:
    if user_id:
        rows = fetch_all(
            "SELECT * FROM audit_logs WHERE user_id = ? ORDER BY timestamp DESC LIMIT ?;",
            (user_id, limit)
        )
    else:
        rows = fetch_all(
            "SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT ?;",
            (limit,)
        )
    for r in rows:
        if r.get("details_json"):
            try:
                r["details"] = json.loads(r["details_json"])
            except Exception:
                r["details"] = {}
    return rows

def get_recent_security_events(limit: int = 50) -> list[dict]:
    rows = fetch_all(
        "SELECT * FROM security_events ORDER BY timestamp DESC LIMIT ?;",
        (limit,)
    )
    for r in rows:
        if r.get("details_json"):
            try:
                r["details"] = json.loads(r["details_json"])
            except Exception:
                r["details"] = {}
    return rows

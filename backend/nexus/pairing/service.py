"""Secure device pairing workflow service."""
from __future__ import annotations
import secrets
import time
from typing import Any, Optional
from ..database.db import execute, fetch_one, transaction
from ..security.tokens import generate_pairing_code
from ..audit.service import log_audit, log_security_event
from ..config import PAIRING_CODE_EXPIRY_SECONDS

def create_pairing_request(device_id: str, device_info: Optional[str] = None, client_ip: Optional[str] = None) -> dict[str, Any]:
    # Ensure device exists and is not revoked
    dev = fetch_one("SELECT id, status, name, model FROM devices WHERE id = ?;", (device_id,))
    if not dev:
        raise ValueError("Device not found")
    if dev["status"] == "REVOKED":
        raise ValueError("Cannot pair a revoked device")

    req_id = f"pr_{secrets.token_hex(8)}"
    code = generate_pairing_code()
    now = time.time()
    expires_at = now + PAIRING_CODE_EXPIRY_SECONDS

    with transaction():
        # Invalidate any old pending requests for this device
        execute("UPDATE pairing_requests SET status = 'EXPIRED' WHERE device_id = ? AND status = 'PENDING';", (device_id,))
        execute(
            """
            INSERT INTO pairing_requests (id, code, device_id, user_id, device_info, status, expires_at, created_at)
            VALUES (?, ?, ?, NULL, ?, 'PENDING', ?, ?);
            """,
            (req_id, code, device_id, device_info or f"{dev['name']} ({dev['model']})", expires_at, now)
        )
        execute("UPDATE devices SET status = 'PAIRING_PENDING', updated_at = ? WHERE id = ?;", (now, device_id))

    log_audit("PAIRING_REQUEST_CREATED", device_id=device_id, details={"code": code, "expires_at": expires_at}, client_ip=client_ip)

    return {
        "pairing_id": req_id,
        "code": code,
        "device_id": device_id,
        "expires_in": PAIRING_CODE_EXPIRY_SECONDS,
        "expires_at": expires_at
    }

def verify_pairing_code(code: str, user_id: str, client_ip: Optional[str] = None) -> dict[str, Any]:
    code = code.strip().upper()
    req = fetch_one("SELECT * FROM pairing_requests WHERE code = ? AND status = 'PENDING';", (code,))
    if not req:
        log_security_event("INVALID_PAIRING_CODE", "LOW", client_ip=client_ip, details={"code": code}, action_taken="DENIED")
        raise ValueError("Invalid or expired pairing code")

    now = time.time()
    if req["expires_at"] < now:
        with transaction():
            execute("UPDATE pairing_requests SET status = 'EXPIRED' WHERE id = ?;", (req["id"],))
        raise ValueError("Pairing code has expired")

    dev = fetch_one("SELECT id, name, model, android_version, app_version FROM devices WHERE id = ?;", (req["device_id"],))
    if not dev:
        raise ValueError("Device associated with code not found")

    with transaction():
        execute("UPDATE pairing_requests SET user_id = ?, status = 'VERIFIED' WHERE id = ?;", (user_id, req["id"],))

    log_audit("PAIRING_CODE_VERIFIED", user_id=user_id, device_id=dev["id"], details={"pairing_id": req["id"]}, client_ip=client_ip)

    return {
        "pairing_id": req["id"],
        "device": dev,
        "status": "VERIFIED",
        "requires_device_approval": True
    }

def approve_pairing(pairing_id: str, device_id: str, client_ip: Optional[str] = None) -> dict[str, Any]:
    """Device explicitly approves pairing request."""
    req = fetch_one("SELECT * FROM pairing_requests WHERE id = ? AND device_id = ?;", (pairing_id, device_id))
    if not req:
        raise ValueError("Pairing request not found")
    if req["status"] != "VERIFIED":
        raise ValueError(f"Pairing request is in invalid status: {req['status']}")
    if req["expires_at"] < time.time():
        with transaction():
            execute("UPDATE pairing_requests SET status = 'EXPIRED' WHERE id = ?;", (req["id"],))
        raise ValueError("Pairing request has expired")

    user_id = req["user_id"]
    if not user_id:
        raise ValueError("No user associated with verified pairing request")

    now = time.time()
    with transaction():
        execute("UPDATE pairing_requests SET status = 'APPROVED' WHERE id = ?;", (pairing_id,))
        execute("UPDATE devices SET user_id = ?, status = 'PAIRED', updated_at = ? WHERE id = ?;", (user_id, now, device_id))

    log_audit("PAIRING_APPROVED", user_id=user_id, device_id=device_id, details={"pairing_id": pairing_id}, client_ip=client_ip)

    return {
        "pairing_id": pairing_id,
        "device_id": device_id,
        "user_id": user_id,
        "status": "PAIRED"
    }

def reject_pairing(pairing_id: str, device_id: str, client_ip: Optional[str] = None) -> bool:
    req = fetch_one("SELECT * FROM pairing_requests WHERE id = ? AND device_id = ?;", (pairing_id, device_id))
    if not req:
        return False
    now = time.time()
    with transaction():
        execute("UPDATE pairing_requests SET status = 'REJECTED' WHERE id = ?;", (pairing_id,))
        execute("UPDATE devices SET status = 'UNREGISTERED', updated_at = ? WHERE id = ?;", (now, device_id))

    log_audit("PAIRING_REJECTED", device_id=device_id, details={"pairing_id": pairing_id}, client_ip=client_ip)
    return True

def get_pairing_status(pairing_id: str) -> Optional[dict[str, Any]]:
    req = fetch_one("SELECT * FROM pairing_requests WHERE id = ?;", (pairing_id,))
    if not req:
        return None
    if req["status"] == "PENDING" and req["expires_at"] < time.time():
        with transaction():
            execute("UPDATE pairing_requests SET status = 'EXPIRED' WHERE id = ?;", (pairing_id,))
        req["status"] = "EXPIRED"
    return req

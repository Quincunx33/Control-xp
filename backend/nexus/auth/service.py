"""Authentication business logic and session lifecycle."""
from __future__ import annotations
import secrets
import time
from typing import Any, Optional
from ..database.db import execute, fetch_one, fetch_all, transaction
from ..security.passwords import hash_password, verify_password
from ..security.tokens import create_token, decode_token
from ..audit.service import log_audit, log_security_event
from ..config import TOKEN_EXPIRY_SECONDS

def register_user(email: str, password: str, name: str, client_ip: Optional[str] = None) -> dict[str, Any]:
    email = email.strip().lower()
    if not email or "@" not in email:
        raise ValueError("Invalid email address")
    if not name or len(name.strip()) < 2:
        raise ValueError("Name must be at least 2 characters")
    if not password or len(password) < 8:
        raise ValueError("Password must be at least 8 characters")

    existing = fetch_one("SELECT id FROM users WHERE email = ?;", (email,))
    if existing:
        raise ValueError("A user with this email address already exists")

    user_id = f"usr_{secrets.token_hex(8)}"
    now = time.time()
    pwd_hash = hash_password(password)

    with transaction():
        execute(
            """
            INSERT INTO users (id, email, password_hash, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?);
            """,
            (user_id, email, pwd_hash, name.strip(), now, now)
        )

    log_audit("USER_REGISTERED", user_id=user_id, details={"email": email, "name": name}, client_ip=client_ip)
    return {"id": user_id, "email": email, "name": name, "created_at": now}

def authenticate_user(email: str, password: str, device_info: str = "Web Browser", client_ip: Optional[str] = None) -> dict[str, Any]:
    email = email.strip().lower()
    user = fetch_one("SELECT * FROM users WHERE email = ?;", (email,))
    if not user:
        log_security_event("LOGIN_FAILED_UNKNOWN_EMAIL", "LOW", client_ip=client_ip, details={"email": email}, action_taken="DENIED")
        raise ValueError("Invalid email or password")

    if not verify_password(password, user["password_hash"]):
        log_security_event("LOGIN_FAILED_WRONG_PASSWORD", "MEDIUM", client_ip=client_ip, details={"email": email, "user_id": user["id"]}, action_taken="DENIED")
        raise ValueError("Invalid email or password")

    now = time.time()
    expires_at = now + TOKEN_EXPIRY_SECONDS
    session_id = f"ses_{secrets.token_hex(8)}"

    token = create_token({
        "sub": user["id"],
        "sid": session_id,
        "email": user["email"],
        "name": user["name"]
    }, expires_in=TOKEN_EXPIRY_SECONDS)

    with transaction():
        execute(
            """
            INSERT INTO sessions (id, user_id, token, device_info, expires_at, created_at, revoked)
            VALUES (?, ?, ?, ?, ?, ?, 0);
            """,
            (session_id, user["id"], token, device_info, expires_at, now)
        )

    log_audit("USER_LOGIN", user_id=user["id"], details={"session_id": session_id, "device_info": device_info}, client_ip=client_ip)

    return {
        "token": token,
        "expires_at": expires_at,
        "user": {
            "id": user["id"],
            "email": user["email"],
            "name": user["name"]
        }
    }

def verify_session(token: str) -> Optional[dict[str, Any]]:
    payload = decode_token(token)
    if not payload or not payload.get("sub"):
        return None

    session_id = payload.get("sid")
    if session_id:
        row = fetch_one(
            "SELECT revoked, expires_at FROM sessions WHERE id = ? AND revoked = 0;",
            (session_id,)
        )
        if not row or row["expires_at"] < time.time():
            return None

    user = fetch_one("SELECT id, email, name FROM users WHERE id = ?;", (payload["sub"],))
    if not user:
        return None

    return {
        "user": user,
        "payload": payload,
        "session_id": session_id
    }

def revoke_session(token: str, client_ip: Optional[str] = None) -> bool:
    payload = decode_token(token)
    session_id = payload.get("sid") if payload else None
    if session_id:
        with transaction():
            execute("UPDATE sessions SET revoked = 1 WHERE id = ?;", (session_id,))
        if payload and payload.get("sub"):
            log_audit("USER_LOGOUT", user_id=payload["sub"], details={"session_id": session_id}, client_ip=client_ip)
        return True
    return False

def get_user_sessions(user_id: str) -> list[dict]:
    rows = fetch_all(
        "SELECT id, device_info, expires_at, created_at, revoked FROM sessions WHERE user_id = ? ORDER BY created_at DESC;",
        (user_id,)
    )
    return rows

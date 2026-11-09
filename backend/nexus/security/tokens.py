"""Cryptographic token and device credential management."""
from __future__ import annotations
import base64
import hashlib
import hmac
import json
import os
import random
import secrets
import string
import time
from typing import Any, Optional
from ..config import JWT_SECRET, TOKEN_EXPIRY_SECONDS

def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")

def _b64url_decode(s: str) -> bytes:
    padding = 4 - (len(s) % 4)
    if padding != 4:
        s += "=" * padding
    return base64.urlsafe_b64decode(s.encode("ascii"))

def create_token(payload: dict[str, Any], secret: Optional[str] = None, expires_in: int = TOKEN_EXPIRY_SECONDS) -> str:
    sec = (secret or JWT_SECRET).encode("utf-8")
    now = time.time()
    data = dict(payload)
    data["iat"] = now
    data["exp"] = now + expires_in
    data["jti"] = secrets.token_hex(8)

    header = {"alg": "HS256", "typ": "JWT"}
    header_b64 = _b64url_encode(json.dumps(header, separators=(",", ":")).encode("utf-8"))
    payload_b64 = _b64url_encode(json.dumps(data, separators=(",", ":")).encode("utf-8"))
    signing_input = f"{header_b64}.{payload_b64}".encode("ascii")
    sig = hmac.new(sec, signing_input, hashlib.sha256).digest()
    sig_b64 = _b64url_encode(sig)
    return f"{header_b64}.{payload_b64}.{sig_b64}"

def decode_token(token: str, secret: Optional[str] = None) -> Optional[dict[str, Any]]:
    if not token or not isinstance(token, str):
        return None
    try:
        parts = token.split(".")
        if len(parts) != 3:
            return None
        header_b64, payload_b64, sig_b64 = parts
        sec = (secret or JWT_SECRET).encode("utf-8")
        signing_input = f"{header_b64}.{payload_b64}".encode("ascii")
        expected_sig = hmac.new(sec, signing_input, hashlib.sha256).digest()
        actual_sig = _b64url_decode(sig_b64)
        if not hmac.compare_digest(expected_sig, actual_sig):
            return None

        payload_bytes = _b64url_decode(payload_b64)
        payload = json.loads(payload_bytes.decode("utf-8"))
        if "exp" in payload and time.time() > payload["exp"]:
            return None
        return payload
    except Exception:
        return None

def generate_device_key() -> str:
    """Generate a high-entropy 256-bit device secret."""
    return f"nxdev_{secrets.token_hex(24)}"

def hash_device_key(device_key: str, salt: str) -> str:
    derived = hashlib.sha256((device_key + salt).encode("utf-8")).digest()
    return base64.b64encode(derived).decode("ascii")

def verify_device_key(device_key: str, salt: str, key_hash: str) -> bool:
    try:
        expected = hash_device_key(device_key, salt)
        return hmac.compare_digest(expected, key_hash)
    except Exception:
        return False

def generate_pairing_code() -> str:
    """Generate a clean, unambiguous 6-character pairing code."""
    alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    return "".join(secrets.choice(alphabet) for _ in range(6))

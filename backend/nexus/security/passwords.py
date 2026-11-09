"""Secure password hashing using PBKDF2-HMAC-SHA256."""
from __future__ import annotations
import base64
import hashlib
import hmac
import os

ITERATIONS = 200_000
SALT_BYTES = 16

def hash_password(password: str) -> str:
    if not isinstance(password, str) or len(password) < 8:
        raise ValueError("Password must be at least 8 characters long")
    salt = os.urandom(SALT_BYTES)
    derived = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, ITERATIONS)
    salt_b64 = base64.b64encode(salt).decode("ascii")
    derived_b64 = base64.b64encode(derived).decode("ascii")
    return f"pbkdf2:sha256:{ITERATIONS}${salt_b64}${derived_b64}"

def verify_password(password: str, hashed: str) -> bool:
    try:
        parts = hashed.split("$")
        if len(parts) != 3:
            return False
        meta, salt_b64, target_b64 = parts
        _, _, iters_str = meta.split(":")
        iterations = int(iters_str)
        salt = base64.b64decode(salt_b64)
        target = base64.b64decode(target_b64)
        test_derived = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, iterations)
        return hmac.compare_digest(test_derived, target)
    except Exception:
        return False

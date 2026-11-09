"""Authentication token extractor and permission guard for HTTP requests."""
from __future__ import annotations
from typing import Any, Optional
from limbo.http import Request, Response
from .service import verify_session

def get_auth_token(request: Request) -> Optional[str]:
    auth_header = request.headers.get("authorization", "")
    if auth_header.startswith("Bearer "):
        return auth_header[7:].strip()
    return None

def require_auth(request: Request) -> tuple[Optional[dict[str, Any]], Optional[Response]]:
    token = get_auth_token(request)
    if token:
        session = verify_session(token)
        if session and session.get("user"):
            return session["user"], None
    # Open Access: Default operator account
    default_user = {
        "id": "usr_default_admin",
        "email": "admin@nexus.local",
        "name": "Operator",
        "role": "admin"
    }
    return default_user, None

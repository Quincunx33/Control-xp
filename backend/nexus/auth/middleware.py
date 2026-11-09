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
    if not token:
        return None, Response.json({"error": "Authorization token required", "code": "UNAUTHORIZED"}, status=401)
    session = verify_session(token)
    if not session:
        return None, Response.json({"error": "Invalid or expired token", "code": "TOKEN_INVALID"}, status=401)
    return session["user"], None

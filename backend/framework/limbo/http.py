"""Limbo HTTP Request and Response primitives."""
from __future__ import annotations
import json
from typing import Any, Optional

class Request:
    def __init__(
        self,
        path: str = "/",
        method: str = "GET",
        headers: Optional[dict[str, str]] = None,
        body: bytes = b"",
        client: Optional[tuple[str, int]] = None
    ):
        self.path = path
        self.method = method.upper()
        self.headers = {k.lower(): v for k, v in (headers or {}).items()}
        self.body = body
        self.client = client or ("127.0.0.1", 8000)
        self.state: dict[str, Any] = {}

    def json(self) -> dict[str, Any]:
        if not self.body:
            return {}
        return json.loads(self.body.decode("utf-8"))

class Response:
    def __init__(self, body: Any = "", status: int = 200, headers: Optional[dict[str, str]] = None):
        self.status = status
        self.headers = headers or {}
        if isinstance(body, (dict, list)):
            self.body = json.dumps(body).encode("utf-8")
            self.headers["Content-Type"] = "application/json"
        elif isinstance(body, str):
            self.body = body.encode("utf-8")
        else:
            self.body = body

    @classmethod
    def json(cls, data: Any, status: int = 200) -> Response:
        return cls(body=data, status=status, headers={"Content-Type": "application/json"})

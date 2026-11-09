"""Limbo middleware utilities."""
from __future__ import annotations
from typing import Callable
from .http import Request, Response

def cors(origin: str = "*") -> Callable:
    def middleware(request: Request, next_handler: Callable) -> Response:
        if request.method == "OPTIONS":
            resp = Response("", status=204)
            resp.headers["Access-Control-Allow-Origin"] = origin
            resp.headers["Access-Control-Allow-Methods"] = "GET, POST, PATCH, DELETE, OPTIONS"
            resp.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
            return resp
        resp = next_handler(request)
        resp.headers["Access-Control-Allow-Origin"] = origin
        return resp
    return middleware

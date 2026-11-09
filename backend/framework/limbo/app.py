"""Limbo App Router and Server Engine."""
from __future__ import annotations
import json
import logging
from typing import Callable, Any, Optional
from .http import Request, Response

logger = logging.getLogger("limbo.app")

class Limbo:
    def __init__(self):
        self.routes: list[tuple[str, str, Callable]] = []
        self.middlewares: list[Callable] = []

    def add_route(self, path: str, method: str, handler: Callable):
        self.routes.append((path, method.upper(), handler))

    def get(self, path: str):
        def decorator(handler: Callable):
            self.add_route(path, "GET", handler)
            return handler
        return decorator

    def post(self, path: str):
        def decorator(handler: Callable):
            self.add_route(path, "POST", handler)
            return handler
        return decorator

    def patch(self, path: str):
        def decorator(handler: Callable):
            self.add_route(path, "PATCH", handler)
            return handler
        return decorator

    def delete(self, path: str):
        def decorator(handler: Callable):
            self.add_route(path, "DELETE", handler)
            return handler
        return decorator

    def use(self, middleware: Callable):
        self.middlewares.append(middleware)

    def handle_request(self, request: Request) -> Response:
        def route_handler(req: Request) -> Response:
            for path, method, handler in self.routes:
                if req.method == method and req.path == path:
                    return handler(req)
            return Response.json({"error": "Not Found"}, status=404)

        handler_chain = route_handler
        for mw in reversed(self.middlewares):
            current_chain = handler_chain
            def make_mw(m, c):
                return lambda req: m(req, c)
            handler_chain = make_mw(mw, current_chain)

        return handler_chain(request)

    def run(self, host: str = "0.0.0.0", port: int = 8000):
        logger.info(f"Limbo app ready on http://{host}:{port}")

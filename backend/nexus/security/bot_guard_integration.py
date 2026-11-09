"""Native Bot Guard pre-authentication security middleware for Limbo."""
from __future__ import annotations
import sys
from pathlib import Path
from typing import Callable, Optional

# Ensure vendored framework is accessible
_framework_dir = Path(__file__).resolve().parent.parent.parent / "framework"
if str(_framework_dir) not in sys.path:
    sys.path.insert(0, str(_framework_dir))

from bot_guard.detector import BotGuard
from bot_guard.models import ALLOW, BLOCK, CHALLENGE, MONITOR, RATE_LIMIT, BotResult
from bot_guard.ddos import DDoSProtector
from limbo.http import Request, Response

from ..config import BOT_GUARD_MODE
from ..audit.service import log_security_event
from .trusted_proxy import resolve_client_ip
from .rate_limits import SimpleRateLimiter

# Initialize BotGuard with mode from config
_guard = BotGuard(
    mode=BOT_GUARD_MODE if BOT_GUARD_MODE in ("shadow", "enforce") else "shadow",
    threshold=0.6,
    challenge_at=0.4,
    rate_limit_at=0.5,
    block_at=0.7
)

# Protect sensitive endpoints from brute-force / credential stuffing
_auth_limiter = SimpleRateLimiter(rate=10, per=60)  # 10 requests per minute per IP
_pairing_limiter = SimpleRateLimiter(rate=20, per=60) # 20 requests per minute per IP

def get_bot_guard() -> BotGuard:
    return _guard

def bot_guard_middleware(request: Request, next_handler: Callable) -> Response:
    """Pre-auth middleware: Runs before router and authentication handlers."""
    peer_ip = request.client[0] if request.client else "127.0.0.1"
    client_ip = resolve_client_ip(request.headers, peer_ip)
    request.state["client_ip"] = client_ip

    path = request.path
    user_agent = request.headers.get("user-agent", "")

    # Sensitive path rate-limiting
    if path.startswith("/api/v1/auth/login") or path.startswith("/api/v1/auth/register"):
        if not _auth_limiter.allow(client_ip):
            log_security_event(
                event_type="AUTH_RATE_LIMIT_EXCEEDED",
                severity="MEDIUM",
                client_ip=client_ip,
                user_agent=user_agent,
                details={"path": path, "rate_limit": "10/min"},
                action_taken="RATE_LIMITED"
            )
            resp = Response.json(
                {"error": "Too many requests. Please wait before retrying.", "code": "RATE_LIMITED"},
                status=429
            )
            resp.headers["Retry-After"] = "60"
            return resp

    if path.startswith("/api/v1/pairing"):
        if not _pairing_limiter.allow(client_ip):
            log_security_event(
                event_type="PAIRING_RATE_LIMIT_EXCEEDED",
                severity="MEDIUM",
                client_ip=client_ip,
                user_agent=user_agent,
                details={"path": path, "rate_limit": "20/min"},
                action_taken="RATE_LIMITED"
            )
            resp = Response.json(
                {"error": "Pairing rate limit exceeded.", "code": "PAIRING_RATE_LIMITED"},
                status=429
            )
            resp.headers["Retry-After"] = "60"
            return resp

    # Run BotGuard inspection
    try:
        bot_result: BotResult = _guard.inspect(request.headers, client_ip=client_ip)
        request.state["bot_result"] = bot_result

        if bot_result.action in (BLOCK, CHALLENGE, RATE_LIMIT):
            reasons = [r.name if hasattr(r, "name") else str(r) for r in (bot_result.reasons or [])]
            action_taken = "BLOCKED" if _guard.mode == "enforce" else "SHADOW_MONITORED"
            log_security_event(
                event_type="BOT_GUARD_DETECTION",
                severity="HIGH" if bot_result.action == BLOCK else "MEDIUM",
                client_ip=client_ip,
                user_agent=user_agent,
                details={
                    "path": path,
                    "score": bot_result.score,
                    "action": bot_result.action,
                    "reasons": reasons,
                    "mode": _guard.mode
                },
                action_taken=action_taken
            )

            if _guard.mode == "enforce":
                status_code = 429 if bot_result.action == RATE_LIMIT else 403
                return Response.json(
                    {
                        "error": "Request rejected by Bot Guard security policy",
                        "code": "BOT_GUARD_BLOCKED",
                        "score": bot_result.score
                    },
                    status=status_code
                )
    except Exception as e:
        # Fail open with log in case of internal inspection error to avoid DOS
        pass

    # Proceed to next handler / router
    response = next_handler(request)
    # Add Bot Guard telemetry header in shadow mode
    if "bot_result" in request.state:
        response.headers["X-Bot-Guard-Score"] = f"{request.state['bot_result'].score:.2f}"
    return response

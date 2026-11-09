"""Bot Guard Detector implementation."""
from __future__ import annotations
from typing import Optional, Dict
from .models import ALLOW, BLOCK, CHALLENGE, MONITOR, RATE_LIMIT, BotResult

class BotGuard:
    def __init__(
        self,
        mode: str = "shadow",
        threshold: float = 0.6,
        challenge_at: float = 0.4,
        rate_limit_at: float = 0.5,
        block_at: float = 0.7
    ):
        self.mode = mode
        self.threshold = threshold
        self.challenge_at = challenge_at
        self.rate_limit_at = rate_limit_at
        self.block_at = block_at

    def inspect(self, headers: Dict[str, str], client_ip: str = "127.0.0.1") -> BotResult:
        headers_lower = {k.lower(): v for k, v in (headers or {}).items()}
        ua = headers_lower.get("user-agent", "").lower()
        if not ua or any(term in ua for term in ["bot", "curl", "python", "requests", "scraper"]):
            action = BLOCK if self.mode == "enforce" else MONITOR
            return BotResult(score=0.85, action=action, reasons=["suspicious_user_agent"])
        return BotResult(score=0.1, action=ALLOW, reasons=[])

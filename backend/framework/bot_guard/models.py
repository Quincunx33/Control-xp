"""Bot Guard models and action constants."""
from __future__ import annotations
from dataclasses import dataclass
from typing import Optional, List

ALLOW = "ALLOW"
MONITOR = "MONITOR"
CHALLENGE = "CHALLENGE"
RATE_LIMIT = "RATE_LIMIT"
BLOCK = "BLOCK"

@dataclass
class BotResult:
    score: float = 0.0
    action: str = ALLOW
    reasons: Optional[List[str]] = None

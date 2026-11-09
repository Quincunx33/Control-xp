"""In-memory rate limiter with thread safety."""
from __future__ import annotations
import threading
import time

class SimpleRateLimiter:
    def __init__(self, rate: int, per: float) -> None:
        self.rate = rate
        self.per = per
        self.lock = threading.Lock()
        self.buckets: dict[str, list[float]] = {}

    def allow(self, key: str) -> bool:
        now = time.time()
        window_start = now - self.per
        with self.lock:
            timestamps = self.buckets.get(key, [])
            valid = [t for t in timestamps if t > window_start]
            if len(valid) >= self.rate:
                self.buckets[key] = valid
                return False
            valid.append(now)
            self.buckets[key] = valid
            # Housekeeping
            if len(self.buckets) > 10000:
                self.cleanup(window_start)
            return True

    def cleanup(self, window_start: float) -> None:
        expired = [k for k, ts in self.buckets.items() if not ts or ts[-1] <= window_start]
        for k in expired:
            del self.buckets[k]

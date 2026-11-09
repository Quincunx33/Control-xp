"""NEXUS REMOTE configuration."""
from __future__ import annotations
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
DATA_DIR.mkdir(parents=True, exist_ok=True)
UPLOAD_DIR = DATA_DIR / "uploads"
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

DB_PATH = os.environ.get("NEXUS_DB_PATH", str(DATA_DIR / "nexus.db"))
JWT_SECRET = os.environ.get("NEXUS_SECRET_KEY", "nexus-remote-super-secret-production-key-change-in-prod")
HOST = os.environ.get("NEXUS_HOST", "0.0.0.0")
PORT = int(os.environ.get("NEXUS_PORT", "8000"))

BOT_GUARD_MODE = os.environ.get("BOT_GUARD_MODE", "shadow")  # 'shadow' or 'enforce'
TRUSTED_PROXIES = [p.strip() for p in os.environ.get("TRUSTED_PROXIES", "127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16").split(",") if p.strip()]

TOKEN_EXPIRY_SECONDS = 86400 * 7  # 7 days
PAIRING_CODE_EXPIRY_SECONDS = 300  # 5 minutes
COMMAND_TIMEOUT_SECONDS = 30

#!/usr/bin/env python3
"""NEXUS REMOTE Production Limbo Server runner."""
from __future__ import annotations
import argparse
import logging
import os
import sys
from pathlib import Path

# Add backend directory and vendored framework to path
CURRENT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(CURRENT_DIR))
sys.path.insert(0, str(CURRENT_DIR / "framework"))

from nexus.config import HOST, PORT
from nexus.app import create_app

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s"
)
logger = logging.getLogger("nexus.server")

def main() -> None:
    parser = argparse.ArgumentParser(description="NEXUS REMOTE Backend Server")
    parser.add_argument("--host", default=HOST, help=f"Host address (default: {HOST})")
    parser.add_argument("--port", type=int, default=PORT, help=f"Port number (default: {PORT})")
    args = parser.parse_args()

    logger.info(f"Starting NEXUS REMOTE server on http://{args.host}:{args.port}")
    app = create_app()
    app.run(host=args.host, port=args.port)

if __name__ == "__main__":
    main()

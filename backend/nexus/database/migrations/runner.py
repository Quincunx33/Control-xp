"""Versioned SQL migration runner for NEXUS REMOTE."""
from __future__ import annotations
import os
import time
from pathlib import Path
from typing import Optional
from ..db import get_connection, transaction

MIGRATIONS_DIR = Path(__file__).resolve().parent

def run_migrations(db_path: Optional[str] = None) -> list[str]:
    conn = get_connection(db_path)
    with transaction(db_path):
        conn.execute("""
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                applied_at REAL NOT NULL
            );
        """)

    cur = conn.execute("SELECT version FROM schema_migrations ORDER BY version ASC;")
    applied = {row[0] for row in cur.fetchall()}

    applied_now = []
    sql_files = sorted(MIGRATIONS_DIR.glob("*.sql"))

    for f in sql_files:
        try:
            version = int(f.name.split("_")[0])
        except (ValueError, IndexError):
            continue

        if version not in applied:
            with open(f, "r", encoding="utf-8") as s:
                sql_content = s.read()

            with transaction(db_path):
                conn.executescript(sql_content)
                conn.execute(
                    "INSERT INTO schema_migrations (version, name, applied_at) VALUES (?, ?, ?);",
                    (version, f.name, time.time())
                )
            applied_now.append(f.name)

    return applied_now

if __name__ == "__main__":
    applied = run_migrations()
    print(f"Migrations complete. Applied: {applied}")

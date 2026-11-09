"""SQLite database helper with WAL mode, foreign keys, and transaction safety."""
from __future__ import annotations
import sqlite3
import threading
from contextlib import contextmanager
from typing import Any, Generator, List, Optional
from ..config import DB_PATH

_tls = threading.local()

def get_connection(db_path: Optional[str] = None) -> sqlite3.Connection:
    path = db_path or DB_PATH
    conn = getattr(_tls, "conn", None)
    if conn is None:
        conn = sqlite3.connect(path, timeout=30.0, check_same_thread=False)
        conn.isolation_level = None  # Enable explicit transaction control
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON;")
        conn.execute("PRAGMA journal_mode = WAL;")
        conn.execute("PRAGMA synchronous = NORMAL;")
        _tls.conn = conn
    return conn

@contextmanager
def transaction(db_path: Optional[str] = None) -> Generator[sqlite3.Connection, None, None]:
    conn = get_connection(db_path)
    try:
        conn.execute("BEGIN IMMEDIATE;")
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise

def execute(query: str, params: tuple | list | dict = (), db_path: Optional[str] = None) -> sqlite3.Cursor:
    conn = get_connection(db_path)
    return conn.execute(query, params)

def fetch_one(query: str, params: tuple | list | dict = (), db_path: Optional[str] = None) -> Optional[dict]:
    conn = get_connection(db_path)
    cur = conn.execute(query, params)
    row = cur.fetchone()
    return dict(row) if row else None

def fetch_all(query: str, params: tuple | list | dict = (), db_path: Optional[str] = None) -> List[dict]:
    conn = get_connection(db_path)
    cur = conn.execute(query, params)
    return [dict(r) for r in cur.fetchall()]

"""Secure file transfer storage and approval service."""
from __future__ import annotations
import hashlib
import os
import secrets
import time
from pathlib import Path
from typing import Any, Optional
from ..database.db import execute, fetch_one, fetch_all, transaction
from ..audit.service import log_audit
from ..config import UPLOAD_DIR

def create_file_transfer(
    device_id: str,
    user_id: str,
    filename: str,
    direction: str,  # UPLOAD, DOWNLOAD
    filesize: int = 0,
    checksum: Optional[str] = None
) -> dict[str, Any]:
    transfer_id = f"ft_{secrets.token_hex(8)}"
    now = time.time()
    clean_name = Path(filename).name

    with transaction():
        execute(
            """
            INSERT INTO file_transfers (id, device_id, user_id, direction, filename, filesize, checksum, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'REQUESTED', ?);
            """,
            (transfer_id, device_id, user_id, direction.upper(), clean_name, filesize, checksum, now)
        )

    log_audit("FILE_TRANSFER_REQUESTED", user_id=user_id, device_id=device_id, details={"transfer_id": transfer_id, "filename": clean_name, "direction": direction})

    return {
        "transfer_id": transfer_id,
        "device_id": device_id,
        "filename": clean_name,
        "direction": direction.upper(),
        "status": "REQUESTED",
        "created_at": now
    }

def save_uploaded_file(transfer_id: str, content: bytes) -> str:
    transfer = fetch_one("SELECT * FROM file_transfers WHERE id = ?;", (transfer_id,))
    if not transfer:
        raise ValueError("Transfer not found")

    dest_dir = UPLOAD_DIR / transfer["device_id"]
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest_path = dest_dir / f"{transfer_id}_{transfer['filename']}"

    with open(dest_path, "wb") as f:
        f.write(content)

    checksum = hashlib.sha256(content).hexdigest()
    now = time.time()

    with transaction():
        execute(
            """
            UPDATE file_transfers
            SET status = 'COMPLETED', filesize = ?, checksum = ?, storage_path = ?, completed_at = ?
            WHERE id = ?;
            """,
            (len(content), checksum, str(dest_path), now, transfer_id)
        )

    log_audit("FILE_TRANSFER_COMPLETED", user_id=transfer["user_id"], device_id=transfer["device_id"], details={"transfer_id": transfer_id, "size": len(content)})
    return str(dest_path)

def get_file_transfer(transfer_id: str, user_id: str) -> Optional[dict[str, Any]]:
    row = fetch_one("SELECT * FROM file_transfers WHERE id = ? AND user_id = ?;", (transfer_id, user_id))
    return row

def list_device_transfers(device_id: str, user_id: str, limit: int = 50) -> list[dict[str, Any]]:
    rows = fetch_all(
        "SELECT * FROM file_transfers WHERE device_id = ? AND user_id = ? ORDER BY created_at DESC LIMIT ?;",
        (device_id, user_id, limit)
    )
    return rows

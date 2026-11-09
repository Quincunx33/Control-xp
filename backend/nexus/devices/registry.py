"""Device registry and connection state lifecycle management."""
from __future__ import annotations
import secrets
import time
from typing import Any, Optional
from ..database.db import execute, fetch_one, fetch_all, transaction
from ..security.tokens import generate_device_key, hash_device_key, verify_device_key
from ..audit.service import log_audit, log_security_event

VALID_STATES = {"UNREGISTERED", "PAIRING_PENDING", "PAIRED", "CONNECTING", "ONLINE", "OFFLINE", "REVOKED"}

def register_device(
    name: str,
    model: str,
    android_version: str,
    app_version: str,
    screen_res: str = "1080x2400",
    user_id: Optional[str] = None,
    client_ip: Optional[str] = None
) -> dict[str, Any]:
    device_id = f"dev_{secrets.token_hex(8)}"
    device_key = generate_device_key()
    salt = secrets.token_hex(16)
    key_hash = hash_device_key(device_key, salt)

    initial_status = "PAIRED" if user_id else "PAIRING_PENDING"
    now = time.time()

    with transaction():
        execute(
            """
            INSERT INTO devices (id, user_id, name, model, android_version, app_version, screen_res, status, last_seen, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """,
            (device_id, user_id, name.strip() or "Android Device", model.strip(), android_version.strip(), app_version.strip(), screen_res.strip(), initial_status, now, now, now)
        )
        execute(
            """
            INSERT INTO device_credentials (device_id, device_key_hash, salt, created_at)
            VALUES (?, ?, ?, ?);
            """,
            (device_id, key_hash, salt, now)
        )
        execute(
            """
            INSERT INTO device_status (device_id, battery_level, charging, battery_temp, storage_free_mb, storage_total_mb, network_type, wifi_ssid, updated_at)
            VALUES (?, 100, 0, 25.0, 32000, 64000, 'WIFI', 'Unknown', ?);
            """,
            (device_id, now)
        )

    log_audit("DEVICE_REGISTERED", user_id=user_id, device_id=device_id, details={"model": model, "status": initial_status}, client_ip=client_ip)

    return {
        "device_id": device_id,
        "device_key": device_key,
        "status": initial_status,
        "name": name,
        "model": model,
        "created_at": now
    }

def verify_device_credentials(device_id: str, device_key: str) -> bool:
    cred = fetch_one("SELECT device_key_hash, salt FROM device_credentials WHERE device_id = ?;", (device_id,))
    if not cred:
        return False
    dev = fetch_one("SELECT status FROM devices WHERE id = ?;", (device_id,))
    if not dev or dev["status"] == "REVOKED":
        return False
    return verify_device_key(device_key, cred["salt"], cred["device_key_hash"])

def set_device_connection_state(device_id: str, new_state: str) -> bool:
    if new_state not in VALID_STATES:
        raise ValueError(f"Invalid device state: {new_state}")
    now = time.time()
    with transaction():
        cur = execute("UPDATE devices SET status = ?, last_seen = ?, updated_at = ? WHERE id = ?;", (new_state, now, now, device_id))
    return cur.rowcount > 0

def update_device_status(
    device_id: str,
    battery_level: Optional[int] = None,
    charging: Optional[bool] = None,
    battery_temp: Optional[float] = None,
    storage_free_mb: Optional[int] = None,
    storage_total_mb: Optional[int] = None,
    network_type: Optional[str] = None,
    wifi_ssid: Optional[str] = None,
    latitude: Optional[float] = None,
    longitude: Optional[float] = None,
    accuracy: Optional[float] = None,
    ram_free_mb: Optional[int] = None,
    ram_total_mb: Optional[int] = None
) -> None:
    now = time.time()
    fields = []
    values = []
    if battery_level is not None:
        fields.append("battery_level = ?")
        values.append(battery_level)
    if charging is not None:
        fields.append("charging = ?")
        values.append(1 if charging else 0)
    if battery_temp is not None:
        fields.append("battery_temp = ?")
        values.append(battery_temp)
    if storage_free_mb is not None:
        fields.append("storage_free_mb = ?")
        values.append(storage_free_mb)
    if storage_total_mb is not None:
        fields.append("storage_total_mb = ?")
        values.append(storage_total_mb)
    if network_type is not None:
        fields.append("network_type = ?")
        values.append(network_type)
    if wifi_ssid is not None:
        fields.append("wifi_ssid = ?")
        values.append(wifi_ssid)
    if latitude is not None:
        fields.append("latitude = ?")
        values.append(latitude)
    if longitude is not None:
        fields.append("longitude = ?")
        values.append(longitude)
    if accuracy is not None:
        fields.append("accuracy = ?")
        values.append(accuracy)
    if ram_free_mb is not None:
        fields.append("ram_free_mb = ?")
        values.append(ram_free_mb)
    if ram_total_mb is not None:
        fields.append("ram_total_mb = ?")
        values.append(ram_total_mb)

    fields.append("updated_at = ?")
    values.append(now)
    values.append(device_id)

    with transaction():
        execute(f"UPDATE device_status SET {', '.join(fields)} WHERE device_id = ?;", tuple(values))
        execute("UPDATE devices SET last_seen = ?, updated_at = ? WHERE id = ?;", (now, now, device_id))

def update_device_permissions(device_id: str, permissions: dict[str, bool]) -> None:
    now = time.time()
    with transaction():
        for perm, granted in permissions.items():
            perm_id = f"prm_{secrets.token_hex(6)}"
            execute(
                """
                INSERT INTO device_permissions (id, device_id, permission, granted, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(device_id, permission) DO UPDATE SET granted = excluded.granted, updated_at = excluded.updated_at;
                """,
                (perm_id, device_id, perm, 1 if granted else 0, now)
            )

def get_device(device_id: str, user_id: Optional[str] = None) -> Optional[dict[str, Any]]:
    query = "SELECT * FROM devices WHERE id = ?"
    params = [device_id]
    if user_id:
        query += " AND user_id = ?"
        params.append(user_id)
    dev = fetch_one(query + ";", tuple(params))
    if not dev:
        return None

    status = fetch_one("SELECT * FROM device_status WHERE device_id = ?;", (device_id,)) or {}
    perms = fetch_all("SELECT permission, granted, updated_at FROM device_permissions WHERE device_id = ?;", (device_id,))
    dev["telemetry"] = status
    dev["permissions"] = {p["permission"]: bool(p["granted"]) for p in perms}
    return dev

def list_devices(user_id: str) -> list[dict[str, Any]]:
    devices = fetch_all("SELECT * FROM devices WHERE user_id = ? ORDER BY created_at DESC;", (user_id,))
    for dev in devices:
        status = fetch_one("SELECT * FROM device_status WHERE device_id = ?;", (dev["id"],)) or {}
        perms = fetch_all("SELECT permission, granted FROM device_permissions WHERE device_id = ?;", (dev["id"],))
        dev["telemetry"] = status
        dev["permissions"] = {p["permission"]: bool(p["granted"]) for p in perms}
    return devices

def rename_device(device_id: str, user_id: str, new_name: str) -> bool:
    new_name = new_name.strip()
    if not new_name:
        raise ValueError("Device name cannot be empty")
    now = time.time()
    with transaction():
        cur = execute("UPDATE devices SET name = ?, updated_at = ? WHERE id = ? AND user_id = ?;", (new_name, now, device_id, user_id))
    return cur.rowcount > 0

def revoke_device(device_id: str, user_id: str, client_ip: Optional[str] = None) -> bool:
    now = time.time()
    with transaction():
        cur = execute("UPDATE devices SET status = 'REVOKED', updated_at = ? WHERE id = ? AND user_id = ?;", (now, device_id, user_id))
        if cur.rowcount > 0:
            execute("DELETE FROM device_credentials WHERE device_id = ?;", (device_id,))
    if cur.rowcount > 0:
        log_audit("DEVICE_REVOKED", user_id=user_id, device_id=device_id, client_ip=client_ip)
        return True
    return False

def delete_device(device_id: str, user_id: str, client_ip: Optional[str] = None) -> bool:
    with transaction():
        cur = execute("DELETE FROM devices WHERE id = ? AND user_id = ?;", (device_id, user_id))
    if cur.rowcount > 0:
        log_audit("DEVICE_DELETED", user_id=user_id, device_id=device_id, client_ip=client_ip)
        return True
    return False

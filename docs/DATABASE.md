# NEXUS REMOTE — Database Schema & Migration Architecture

## 1. Engine & Persistence Architecture
- **Engine**: SQLite 3 with Write-Ahead Logging (`PRAGMA journal_mode = WAL;`)
- **Foreign Keys**: Enforced via `PRAGMA foreign_keys = ON;`
- **Concurrency**: Immediate transaction locking (`BEGIN IMMEDIATE;`) for race-free consistency
- **Portability**: Schema is strictly ANSI SQL compatible, allowing migration to PostgreSQL by swapping connection drivers without altering application query structure.

## 2. Table Specifications

### `users`
- `id` (TEXT PK)
- `email` (TEXT UNIQUE)
- `password_hash` (TEXT): Salted PBKDF2-HMAC-SHA256
- `name` (TEXT)
- `created_at` (REAL), `updated_at` (REAL)

### `sessions`
- `id` (TEXT PK)
- `user_id` (TEXT FK -> users.id)
- `token` (TEXT UNIQUE)
- `device_info` (TEXT)
- `expires_at` (REAL), `created_at` (REAL)
- `revoked` (INTEGER 0/1)

### `devices`
- `id` (TEXT PK)
- `user_id` (TEXT FK -> users.id, nullable)
- `name` (TEXT)
- `model` (TEXT)
- `android_version` (TEXT)
- `app_version` (TEXT)
- `screen_res` (TEXT)
- `status` (TEXT): `UNREGISTERED`, `PAIRING_PENDING`, `PAIRED`, `CONNECTING`, `ONLINE`, `OFFLINE`, `REVOKED`
- `last_seen` (REAL), `created_at` (REAL), `updated_at` (REAL)

### `device_credentials`
- `device_id` (TEXT PK FK -> devices.id)
- `device_key_hash` (TEXT): Salted SHA-256 hash
- `salt` (TEXT)
- `created_at` (REAL)

### `pairing_requests`
- `id` (TEXT PK)
- `code` (TEXT UNIQUE): 6-char alphanumeric code
- `device_id` (TEXT FK -> devices.id)
- `user_id` (TEXT FK -> users.id, nullable)
- `device_info` (TEXT)
- `status` (TEXT): `PENDING`, `VERIFIED`, `APPROVED`, `REJECTED`, `EXPIRED`
- `expires_at` (REAL), `created_at` (REAL)

### `device_permissions`
- `id` (TEXT PK)
- `device_id` (TEXT FK -> devices.id)
- `permission` (TEXT)
- `granted` (INTEGER 0/1)
- `updated_at` (REAL)

### `device_status` (Live Telemetry)
- `device_id` (TEXT PK FK -> devices.id)
- `battery_level` (INTEGER)
- `charging` (INTEGER 0/1)
- `battery_temp` (REAL)
- `storage_free_mb` (INTEGER), `storage_total_mb` (INTEGER)
- `network_type` (TEXT), `wifi_ssid` (TEXT)
- `updated_at` (REAL)

### `commands` & `command_results`
- Full command execution lifecycle, dispatched timestamp, received timestamp, JSON parameter schemas, and output validation.

### `audit_logs` & `security_events`
- Immutably tracks every administrative action, IP address, severity level, and Bot Guard scoring.

## 3. Migration Runner
Located at `backend/nexus/database/migrations/runner.py`.
Migrations run automatically on server boot, recording applied scripts in `schema_migrations`.

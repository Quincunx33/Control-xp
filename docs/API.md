# NEXUS REMOTE — REST API Specification (v1)

Base URL: `http://localhost:8000/api/v1` (or `https://your-domain.com/api/v1`)

All authenticated endpoints require the HTTP header:
`Authorization: Bearer <session_token>`

---

## 1. System Health

### `GET /api/v1/health`
Checks server and security engine status.
- **Response `200 OK`**:
```json
{
  "status": "healthy",
  "service": "NEXUS REMOTE",
  "version": "1.0.0",
  "bot_guard": "active"
}
```

---

## 2. Authentication

### `POST /api/v1/auth/register`
Creates a new operator account.
- **Request Body**:
```json
{
  "name": "Operator Name",
  "email": "operator@nexus.local",
  "password": "SecurePassword123!"
}
```
- **Response `201 Created`**:
```json
{
  "user": {
    "id": "usr_94b1a45c",
    "name": "Operator Name",
    "email": "operator@nexus.local",
    "created_at": 1731154000.0
  },
  "message": "User registered successfully"
}
```

### `POST /api/v1/auth/login`
Authenticates user credentials and issues an HMAC-signed session token.
- **Request Body**:
```json
{
  "email": "operator@nexus.local",
  "password": "SecurePassword123!",
  "device_info": "Firefox on Linux"
}
```
- **Response `200 OK`**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expires_at": 1731758800.0,
  "user": {
    "id": "usr_94b1a45c",
    "name": "Operator Name",
    "email": "operator@nexus.local"
  }
}
```

### `POST /api/v1/auth/logout`
Revokes the current session token in the database.

---

## 3. Device Fleet Management

### `GET /api/v1/devices`
Lists all devices registered under the authenticated user.

### `POST /api/v1/devices/register`
Registers a newly installed Android agent installation.
- **Request Body**:
```json
{
  "name": "Pixel 8 Pro",
  "model": "Pixel 8 Pro",
  "android_version": "14",
  "app_version": "1.0.0",
  "screen_res": "1344x2992"
}
```
- **Response `201 Created`**:
```json
{
  "device_id": "dev_4b92af10",
  "device_key": "nxdev_8e4a19cf7b...",
  "status": "PAIRING_PENDING",
  "name": "Pixel 8 Pro",
  "model": "Pixel 8 Pro",
  "created_at": 1731154200.0
}
```

### `GET /api/v1/devices/{id}`
Returns complete device profile, live telemetry, and permission states.

### `PATCH /api/v1/devices/{id}`
Renames device (`{"name": "New Name"}`).

### `POST /api/v1/devices/{id}/revoke`
Revokes device credentials immediately, terminating all access.

---

## 4. Secure Pairing Workflow

### `POST /api/v1/pairing/create`
Android app initiates a short-lived pairing session.
- **Request Body**: `{"device_id": "dev_4b92af10"}`
- **Response `201 Created`**:
```json
{
  "pairing_id": "pr_1048df92",
  "code": "7K9B2X",
  "device_id": "dev_4b92af10",
  "expires_in": 300,
  "expires_at": 1731154500.0
}
```

### `POST /api/v1/pairing/verify`
Web dashboard operator enters pairing code.
- **Request Body**: `{"code": "7K9B2X"}`
- **Response `200 OK`**:
```json
{
  "pairing_id": "pr_1048df92",
  "status": "VERIFIED",
  "requires_device_approval": true
}
```

### `POST /api/v1/pairing/approve`
Device user confirms pairing on Android screen.
- **Request Body**: `{"pairing_id": "pr_1048df92", "device_id": "dev_4b92af10"}`
- **Response `200 OK`**: Associates device with operator account.

---

## 5. Remote Commands

### `POST /api/v1/devices/{id}/commands`
Dispatches a strictly registered command to the connected device.
- **Available Commands**:
  - `ping`: Latency check
  - `sync_status`: Telemetry refresh
  - `battery_check`: Voltage and health check
  - `storage_info`: Filesystem query
  - `vibrate_device`: `{"duration_ms": 500}`
  - `notification_alert`: `{"title": "Alert", "message": "Notice"}`
  - `request_screen_share`: `{"session_id": "st_123"}`
  - `inject_touch`: `{"x": 540, "y": 1200, "duration_ms": 50}`
  - `inject_swipe`: `{"start_x": 540, "start_y": 1800, "end_x": 540, "end_y": 600, "duration_ms": 300}`
  - `press_key`: `{"key": "BACK|HOME|RECENTS|NOTIFICATIONS|QUICK_SETTINGS|POWER_DIALOG|LOCK_SCREEN"}`
  - `set_clipboard`: `{"text": "Text content"}`
  - `get_clipboard`: Fetch device clipboard text
  - `get_location`: Live GPS location fix
  - `play_siren`: Trigger loud 100% volume panic alarm & camera strobe
  - `stop_siren`: Silence alarm
  - `list_apps`: List installed packages
  - `open_app`: `{"package_name": "com.example.app"}`
  - `browse_files`: `{"path": "/storage/emulated/0"}`
  - `system_diagnostics`: CPU cores, RAM memory, and OS uptime
  - `file_transfer_request`: `{"transfer_id": "ft_123", "filename": "file.pdf"}`

### `GET /api/v1/commands/{id}`
Queries command execution status and result payload.

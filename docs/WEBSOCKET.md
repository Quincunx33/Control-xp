# NEXUS REMOTE — WebSocket & WebRTC Protocols

## 1. WebSocket Protocol (`/ws`)

### 1.1 Connection Handshake & Authentication
Both Android Agents and Web Dashboard clients connect to `/ws`. Unauthenticated connections are allowed 5 seconds to authenticate:

- **Device Authentication Frame**:
```json
{
  "type": "auth",
  "device_id": "dev_4b92af10",
  "device_key": "nxdev_8e4a19cf7b..."
}
```
- **Operator Authentication Frame**:
```json
{
  "type": "auth",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```
On success, server returns `{"type": "auth_ok", "role": "device"|"user"}`.

### 1.2 Heartbeat & Telemetry
Devices stream telemetry frames every 30 seconds:
```json
{
  "type": "status_update",
  "device_id": "dev_4b92af10",
  "battery_level": 88,
  "charging": true,
  "battery_temp": 28.5,
  "storage_free_mb": 64200,
  "storage_total_mb": 128000,
  "network_type": "WIFI",
  "wifi_ssid": "Lab-Secure",
  "permissions": {
    "android.permission.CAMERA": true,
    "android.permission.VIBRATE": true
  }
}
```
The gateway automatically broadcasts this telemetry to all active dashboard sessions owned by the device's user.

### 1.3 Command Dispatch & Result Flow
1. Dashboard issues command -> backend creates DB record with `PENDING` status.
2. Gateway checks device socket -> sends:
```json
{
  "type": "command",
  "command_id": "cmd_a891e4",
  "command_type": "vibrate_device",
  "params": { "duration_ms": 500 }
}
```
3. Device `CommandExecutor` executes action -> returns:
```json
{
  "type": "command_result",
  "device_id": "dev_4b92af10",
  "command_id": "cmd_a891e4",
  "result": { "vibrated": true, "duration_ms": 500 }
}
```
4. Backend updates DB to `SUCCESS` and broadcasts result to dashboard.

---

## 2. WebRTC Screen Streaming Architecture

```
Android MediaProjection
         |
         v
    WebRTC Encoder
         |
         v
 Signaling (Offer/Answer/Candidate via WebSocket)
         |
         v
   Limbo Gateway
         |
         v
    Web Dashboard (HTML5 <video> / RTCPeerConnection)
```

- **Signaling Frames**: Multiplexed through `/ws` with message types:
  - `webrtc_offer`
  - `webrtc_answer`
  - `webrtc_candidate`
  - `webrtc_stop`
- **STUN Servers**: Public STUN (`stun:stun.l.google.com:19302`) configured by default. Self-hosted TURN servers can be configured in `.env` for symmetric NAT environments.

# NEXUS REMOTE — Android Permissions & Security Policies

## 1. Declared Permissions & Purposes

| Permission | Protection Level | Purpose in NEXUS REMOTE |
|---|---|---|
| `INTERNET` | Normal | Secure HTTPS REST and WebSocket communication with backend |
| `ACCESS_NETWORK_STATE` | Normal | Detection of network connectivity, link type, and offline transitions |
| `ACCESS_WIFI_STATE` | Normal | Querying Wi-Fi SSID for connection health telemetry |
| `VIBRATE` | Normal | Physical locating alerts triggered via authorized `vibrate_device` command |
| `POST_NOTIFICATIONS` | Runtime (API 33+) | Persistent foreground notification during screen capture and admin notices |
| `FOREGROUND_SERVICE` | Normal | Foreground execution of screen capture service |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Normal (API 34+) | System foreground service type for active screen sharing |
| `CAMERA` | Runtime | QR pairing code scanning and authorized camera sensor diagnostics |
| `RECORD_AUDIO` | Runtime | Optional two-way audio during authorized management sessions |

## 2. Interactive Consent Rules
- **No Background Activation**: The camera or microphone will never record in the background without explicit user action.
- **MediaProjection Dialog**: Android enforces that `createScreenCaptureIntent()` shows a non-bypassable system prompt showing the user what is about to be recorded.
- **Persistent Notification**: While screen sharing is active, `ScreenCaptureService` displays a sticky system notification with a one-tap **Stop Sharing** action.
- **Settings Deep Link**: The app includes a direct intent to the system Application Details Settings screen to grant or revoke runtime permissions at will.

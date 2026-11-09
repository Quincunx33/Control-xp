# NEXUS REMOTE — Architecture Specification

## 1. System Overview

NEXUS REMOTE is a secure, production-grade remote Android device management platform built from scratch. It connects a native Kotlin Android client and a lightweight HTML/CSS/Vanilla JavaScript website to the same team-operated hosted Limbo backend. Both clients share account and authorized-device data; the website is an alternative entry point, not a requirement for app users.

```
                  NEXUS REMOTE
                       |
          +------------+------------+
          |                         |
          v                         v
     WEB DASHBOARD             ANDROID AGENT
   HTML/CSS/Vanilla JS              Kotlin
          |                         |
          +------------+------------+
                       |
                     HTTPS
                       |
                       v
                  CLOUDFLARE
                       |
                       v
                  BOT GUARD
               Pre-auth Security
                       |
                       v
                  LIMBO SERVER
                       |
          +------------+-------------+
          |            |             |
          v            v             v
       REST API     WebSocket     WebRTC
          |            |             |
          +------------+-------------+
                       |
                       v
                    DATABASE
                       |
          +------------+-------------+
          |            |             |
          v            v             v
       Accounts     Devices       Sessions
          |
          v
      Audit Logs
```

## 2. Core Components

### 2.1 Android Agent (`app/`)
- **Native Kotlin**: Built using modern Kotlin 2.2 and Jetpack Compose Material 3.
- **MVVM Architecture**: `AgentViewModel` maintains reactive state with `MutableStateFlow` and Coroutines.
- **Official System Integrations**:
  - `MediaProjectionManager`: Official screen capture dialog with a persistent foreground service notification (`ScreenCaptureService`).
  - `BatteryManager`: Real-time battery level, voltage, temperature, and fast-charging status.
  - `StatFs`: Filesystem partition usage.
  - `ConnectivityManager` & `WifiManager`: Live link state and SSID.
  - `Vibrator` / `VibratorManager`: Haptic locating alerts.
- **Device Pairing**: 6-character short-lived pairing workflow with explicit user confirmation before association.

### 2.2 Limbo Backend Server (`backend/`)
- **Limbo Framework**: The pinned `limbo33` PyPI package; its runtime uses the Python standard library.
- **Bot Guard Integration**: The pinned `bot-guard-py` PyPI package; provides pre-auth request inspection alongside app-level rate limiting and trusted proxy resolution.
- **SQLite with WAL**: Thread-safe database with automated versioned migrations (`schema_migrations`).
- **Cryptographic Security**: PBKDF2-HMAC-SHA256 password hashing (200,000 iterations) and HMAC-SHA256 session tokens.
- **WebSocket Gateway**: Authenticates both Android agents and web dashboard clients; multiplexes presence, commands, telemetry, and WebRTC signaling.

### 2.3 Web Dashboard (`web/`)
- **Zero-Framework Architecture**: Modular Vanilla HTML5, Modern CSS, and ES6 JavaScript.
- **Real-Time Synchronized**: Automatic WebSocket link with exponential backoff.
- **WebRTC Stream Viewer**: Displays live screen streams with STUN/TURN traversal.
- **Cybernetic Theme**: Accessible, high-contrast dark palette tailored for network operations.


## 3. Shared account and client model

The official website and Android app are peer clients of one backend:
- A user may register or log in through either client.
- Both clients use the same account identity, authorization rules and device registry.
- Each client maintains its own session; raw credentials are not copied between clients.
- The backend is authoritative for account ownership, pairing, permissions, commands and audit records.

## 4. Kotlin, Backend and HTML/JavaScript data flow

The normal integration is API-based:
1. A Kotlin repository serializes a typed request as JSON and sends it over HTTPS.
2. The backend authenticates the session, validates the payload, checks device authorization and performs the operation.
3. The backend returns JSON. Kotlin maps it to data models and exposes state through ViewModel/StateFlow to Compose.
4. Website JavaScript calls the same API and updates relevant DOM elements from the JSON response.
5. Authenticated WebSocket events notify both clients about live presence, telemetry and command results.

HTML is the website presentation layer. Kotlin should request data or an explicitly supported operation; it should not send arbitrary HTML or JavaScript for execution. Any future feature that edits user-owned HTML templates must use a dedicated validated content API, preview, version history and rollback—not unrestricted code execution.

## 5. Optional WebView bridge

The current Android UI is native Jetpack Compose. If a future feature intentionally embeds a web editor or dashboard:
- Use WebView only for the trusted official HTTPS origin.
- Expose a small, documented message interface and validate every message and origin.
- Prefer structured JSON and allowlisted actions over arbitrary JavaScript evaluation.
- Never expose session tokens, filesystem access or privileged Android methods to untrusted web content.
- Keep sensitive operations in Kotlin/backend authorization and require confirmation where appropriate.

This bridge is optional and separate from the normal HTTPS/WebSocket architecture. See DATA_FLOW.md for detailed flows.

# NEXUS REMOTE — Architecture Specification

## 1. System Overview

NEXUS REMOTE is a secure, production-grade remote Android device management platform built from scratch. It connects an on-device Android Agent, a self-hostable Limbo backend server, and a lightweight, framework-free browser management dashboard.

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
- **Limbo Framework**: The `limbo33` package, installed from a pinned upstream GitHub commit; its runtime uses the Python standard library.
- **Bot Guard Integration**: The `bot-guard-py` package, installed from a pinned upstream GitHub commit; provides pre-auth request inspection alongside app-level rate limiting and trusted proxy resolution.
- **SQLite with WAL**: Thread-safe database with automated versioned migrations (`schema_migrations`).
- **Cryptographic Security**: PBKDF2-HMAC-SHA256 password hashing (200,000 iterations) and HMAC-SHA256 session tokens.
- **WebSocket Gateway**: Authenticates both Android agents and web dashboard clients; multiplexes presence, commands, telemetry, and WebRTC signaling.

### 2.3 Web Dashboard (`web/`)
- **Zero-Framework Architecture**: Modular Vanilla HTML5, Modern CSS, and ES6 JavaScript.
- **Real-Time Synchronized**: Automatic WebSocket link with exponential backoff.
- **WebRTC Stream Viewer**: Displays live screen streams with STUN/TURN traversal.
- **Cybernetic Theme**: Accessible, high-contrast dark palette tailored for network operations.

# NEXUS REMOTE

> **Secure Android Remote Device Management Platform**  
> Complete Production Specification & Implementation: Native Android Agent, Zero-Framework Web Dashboard, and Self-Hostable Limbo Backend with Bot Guard Integration.

---

## 🌟 Key Highlights

- 📱 **Native Android Agent**: Built with modern Kotlin, Jetpack Compose, and MVVM architecture. Captures real hardware battery, temperature, storage, and network telemetry without mock data.
- 💻 **Zero-Framework Web Dashboard**: Pure Vanilla HTML5, modern CSS, and ES6 JavaScript. No React, Vue, or heavy node dependencies required.
- 🛡️ **Limbo Backend with Bot Guard Security**: Python backend using the separately maintained Limbo and `bot-guard-py` packages, with pre-auth request inspection, IP rate limiting, and trusted proxy verification.
- 🔒 **Secure Device Pairing**: Short-lived 6-character pairing codes with mandatory explicit on-device user confirmation before access is granted.
- 📡 **Real-Time WebSocket & WebRTC**: Authenticated WebSocket gateway for live presence, telemetry streaming, strict command delivery, and WebRTC screen sharing signaling.
- 🎥 **MediaProjection Screen Sharing**: Android system consent workflow with persistent foreground service notification (`ScreenCaptureService`) and one-tap stop controls.
- 📦 **Pinned Python Dependencies**: Limbo and Bot Guard are installed from PyPI as `limbo33` and `bot-guard-py`, with versions pinned in `requirements.txt`.

---

## 🏗️ Architecture

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
                    DATABASE (SQLite + WAL)
                       |
          +------------+-------------+
          |            |             |
          v            v             v
       Accounts     Devices       Sessions
          |
          v
      Audit Logs
```

---

## 🚀 Quick Start

### 1. Install Backend Dependencies & Launch
```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
# Run database migrations and start server on port 8000
bash scripts/start_backend.sh
```
Access the management console at: `http://localhost:8000`

### 2. Run Android Agent
Open the project in Android Studio or compile via Gradle:
```bash
gradle :app:assembleDebug
```
Launch on your Android device or emulator. The default server address points to `http://10.0.2.2:8000` for Android emulators.

### 3. Pair a Device
1. Open the Android Agent on the device and tap **Pair Device** to display the 6-character code (e.g. `7K9B2X`).
2. Log into the Web Dashboard, click **+ Pair Device**, and enter the code.
3. The Android screen will show an authorization prompt: tap **Approve & Link**.
4. The device is now active in your fleet with live telemetry and remote commands.

---

## 🧪 Testing

### Backend Test Suite (10/10 Passing)
```bash
source .venv/bin/activate
python3 backend/tests/test_backend.py
```
Validates password hashing, token expiration, user registration/login, device registration, pairing state transitions, command execution, Bot Guard scoring, and trusted proxy extraction.

### Android Unit & Robolectric Tests (Passing)
```bash
gradle :app:testDebugUnitTest
```
Validates resource strings, SessionManager persistence, and CommandExecutor execution.

---

## 📚 Complete Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — System architecture and topology
- [`docs/API.md`](docs/API.md) — Versioned REST API reference (v1)
- [`docs/DATABASE.md`](docs/DATABASE.md) — SQLite schema and migration system
- [`docs/SECURITY.md`](docs/SECURITY.md) — Bot Guard integration, rate limiting, and password hashing
- [`docs/ANDROID_PERMISSIONS.md`](docs/ANDROID_PERMISSIONS.md) — Android permission explanations and privacy guarantees
- [`docs/WEBSOCKET.md`](docs/WEBSOCKET.md) — Real-time WebSocket and WebRTC signaling protocols
- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) — Linux VPS, systemd, and Nginx deployment guide
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) — Local development workflow and commands
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — Master implementation audit and milestones

---

## 📜 License
MIT License. Built for secure Android device management.

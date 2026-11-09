<!--
  NEXUS REMOTE
  Cloud-hosted Android device management platform.
-->

<div align="center">

# ✦ NEXUS REMOTE

### One account. Two ways to connect. Your devices, under your control.

A cloud-hosted platform for managing Android devices you own or are explicitly authorized to manage.

<p>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Backend-Python-3776AB?style=for-the-badge&logo=python&logoColor=white" alt="Python">
  <img src="https://img.shields.io/badge/Service-Cloud%20Hosted-4C8BF5?style=for-the-badge" alt="Cloud hosted">
  <img src="https://img.shields.io/badge/Status-Active%20Development-orange?style=for-the-badge" alt="Active development">
</p>

[Overview](#-overview) · [Features](#-platform-at-a-glance) · [API](#-python-backend-api) · [Developer setup](#-developer-setup) · [Roadmap](docs/ROADMAP.md)

[Architecture](docs/ARCHITECTURE.md) · [Data flow](docs/DATA_FLOW.md) · [Deployment](docs/DEPLOYMENT.md) · [Report an issue](https://github.com/Quincunx33/Control-xp/issues)

</div>

---

## ✦ Overview

**NEXUS REMOTE** is an Android device management platform built around one shared online service. It brings together a native Android application, an official website and a Python backend operated by the project team.

You can use either client:

- **Android app:** create an account, sign in, pair devices and manage them directly from your phone.
- **Official website:** register or sign in with the same account and use the optional web dashboard to manage authorized devices.
- **One shared account:** your account and device access are managed centrally, so you do not need separate credentials for the app and website.
- **One hosted backend:** both clients connect to the same project-operated online service.

> **No self-hosting for regular users.** You do not need to rent a VPS, install Python, configure a server or run your own backend. The project team intends to operate the official service.

<sub>NEXUS REMOTE is under active development. Features and APIs may change. Review the roadmap and implementation before relying on any capability.</sub>

## ◈ Platform at a glance

| Component | Role |
|---|---|
| **Android application** | Native Kotlin and Jetpack Compose client |
| **Official website** | Registration, login and optional device dashboard |
| **Python backend** | Shared API and service layer, hosted by the project team |
| **Limbo** | Python framework used by the backend |
| **Bot Guard** | Application-level request protection |
| **SQLite** | Current database technology documented by the project |
| **HTTP / WebSocket** | API requests and real-time communication |
| **WebRTC** | Communication technology used by the project where applicable |

### How the platform fits together

```text
       ANDROID APP                         OFFICIAL WEBSITE
    Kotlin / Compose                     HTML / CSS / JavaScript
           |                                      |
           |              HTTPS / WSS             |
           +------------------+-------------------+
                              |
                              v
                    PROJECT-HOSTED BACKEND
                         Python / Limbo
                              |
                         Bot Guard
                              |
                           Database
                              |
                    Authorized device services
```

Both clients use the same backend. The website is optional for app users, and the Android app can be used without opening the website.

## ◆ Core capabilities

The project is being developed around these areas:

- **Account access** — registration, login, logout and session management.
- **Device pairing** — connect an account with devices through an approval-based flow.
- **Device management** — view enrolled devices and their reported status.
- **Remote commands** — a controlled command system for authorized devices.
- **File transfer** — user-authorized transfer workflows.
- **Real-time events** — WebSocket communication for connection and device updates.
- **Security and audit** — authentication, ownership checks and security event records.

Feature availability depends on implementation status. Planned capabilities are listed in the [roadmap](docs/ROADMAP.md); this list should not be read as a claim that every feature is already production-ready.

## ⌘ Python backend API

The Android application and official website are designed to use the same versioned Python API. HTTP routes use the `/api/v1/` prefix.

| Category | Endpoint paths |
|---|---|
| **Authentication** | `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/logout`, `/api/v1/auth/me`, `/api/v1/auth/sessions` |
| **Devices** | `/api/v1/devices`, `/api/v1/devices/register`, `/api/v1/devices/{id}`, `/api/v1/devices/{id}/status` |
| **Pairing** | `/api/v1/pairing/create`, `/api/v1/pairing/verify`, `/api/v1/pairing/approve`, `/api/v1/pairing/reject` |
| **Commands** | `/api/v1/commands/registry`, `/api/v1/devices/{id}/commands`, `/api/v1/commands/{id}` |
| **File transfer** | `/api/v1/files/create`, `/api/v1/files/upload/{id}`, `/api/v1/files/device/{id}` |
| **Audit and security** | `/api/v1/audit`, `/api/v1/security/events` |
| **Real-time** | `/api/v1/ws` (WebSocket) |
| **Health** | `/api/v1/health` |

<sub><strong>API status:</strong> This table records the API surface documented for the project. Confirm each route, HTTP method, request schema and deployment status against the current Python server implementation before using it in a production client. Documentation alone does not guarantee that a route is implemented or enabled.</sub>

### Example: sign in

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password"
}
```

After a successful login, the backend returns the session information defined by its response schema. The client uses the session token for protected requests.

### Example: retrieve devices

```http
GET /api/v1/devices
Authorization: Bearer SESSION_TOKEN
```

The exact response fields and error format must match the server's implemented JSON contract. Never put real passwords or session tokens in documentation, screenshots, source control or logs.

### Client integration and security notes

- **Kotlin API layer:** keep HTTP calls in a shared API client/repository. Use typed DTOs, structured error handling, sensible timeouts and session-expiry handling.
- **WebSocket authentication:** follow the backend's documented authentication message or handshake. Do not assume every WebSocket client can attach an HTTP Bearer header.
- **JSON contracts:** define request bodies, success responses, validation errors and status codes consistently, then keep Kotlin DTOs aligned with them.
- **Server-side authorization:** enforce account ownership and device authorization on every device, command, status and file operation. Hiding a button in the UI is not access control.
- **File upload safety:** verify ownership of the target device and upload session; validate size and type; use safe storage names and integrity checks.
- **CORS:** allow only the official website origins that require browser access. CORS is not authentication and does not replace server-side authorization.
- **Security auditing:** validate inputs, protect sessions, rate-limit sensitive actions, record relevant security events and keep secrets out of logs.

For architecture and message-flow details, see [DATA_FLOW.md](docs/DATA_FLOW.md).

## 🧰 Built with

| Layer | Technologies |
|---|---|
| Android | Kotlin, Jetpack Compose |
| Backend | Python, [Limbo](https://github.com/Quincunx33/limbo) |
| Request protection | [Bot Guard](https://github.com/Quincunx33/bot-guard-py) |
| Database | SQLite |
| Web client | HTML, CSS, Vanilla JavaScript |
| Communication | HTTP, WebSocket, WebRTC |

## 🚀 Using the service

The intended regular-user experience is simple:

1. Install and open the Android app, or visit the official NEXUS REMOTE website.
2. Register an account or sign in.
3. Use the same account on either platform.
4. Pair a device using the approval flow.
5. Manage authorized devices from the Android app or the website dashboard.

**You do not need to host anything yourself.** The project team intends to provide and maintain the official website and Python backend. An internet connection is required for clients and enrolled devices to communicate with the hosted service.

## 🧑‍💻 Developer setup

This section is for contributors who want to run the project locally. It is **not** required for normal users.

### Prerequisites

- Git
- Python version supported by the project's dependencies
- Android Studio (only if building the Android application)

### 1. Clone the repository

```bash
git clone https://github.com/Quincunx33/Control-xp.git
cd Control-xp
```

### 2. Create and activate a virtual environment

**Linux / macOS**

```bash
python3 -m venv .venv
source .venv/bin/activate
```

**Windows PowerShell**

```powershell
py -m venv .venv
.venv\Scripts\Activate.ps1
```

### 3. Install dependencies

```bash
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

### 4. Start the local backend

On Linux, macOS or a shell with Bash available:

```bash
bash scripts/start_backend.sh
```

The startup script prepares the database and starts the backend. The documented default local address is:

```text
http://127.0.0.1:8000
```

If startup fails, review the terminal output and include relevant, sanitized logs when opening an issue. Remove passwords, tokens and other private information before sharing logs.

## 📱 Build the Android app

1. Install [Android Studio](https://developer.android.com/studio).
2. Open the cloned repository.
3. Allow Gradle to sync.
4. Configure the backend address for your development environment.
5. Select an emulator or connected Android device and run the app.

A command-line debug build can also be attempted with:

```bash
gradle :app:assembleDebug
```

The Android device must be able to reach the backend. In the standard Android emulator, `10.0.2.2` is a special address that points to the host computer.

## 🗺️ Roadmap

NEXUS REMOTE is actively evolving. The roadmap tracks the work planned for the official hosted service, standalone Android experience, app updates, remote administration capabilities and stability improvements.

- **Official hosted service:** project-operated website and backend, secure deployment, monitoring, backups and recovery.
- **Standalone Android experience:** complete in-app registration, login, pairing and device management.
- **App updates:** version information, release notifications, release notes and a safe user-confirmed update flow.
- **Remote administration:** consent-based screen viewing, approved file transfer, device telemetry and supported remote assistance actions.
- **Reliability:** reconnect handling, offline states, automated tests and usability improvements.

See [docs/ROADMAP.md](docs/ROADMAP.md) for phases and current progress. Roadmap items are plans until their implementation is completed and verified.

## 🔐 Security and responsible use

NEXUS REMOTE is intended only for devices you own or are explicitly authorized to manage.

- Require authentication and server-side authorization for protected actions.
- Use an approval-based pairing process and short-lived pairing codes.
- Respect Android permissions and consent requirements for sensitive capabilities.
- Screen capture must use Android's visible MediaProjection consent flow and applicable foreground-service requirements.
- Provide clear device-side indicators and a way to stop or revoke access.
- Never use the project to access another person's device without permission.
- Never commit passwords, API keys, access tokens or private signing keys.

Bot Guard provides an application-level protection layer. It does not replace secure infrastructure configuration, firewall rules or infrastructure-level DDoS protection.

## 📖 Documentation

| Guide | Description |
|---|---|
| [Architecture](docs/ARCHITECTURE.md) | Main components and how they fit together |
| [Data flow](docs/DATA_FLOW.md) | Android, website and backend communication |
| [Deployment](docs/DEPLOYMENT.md) | Backend deployment notes |
| [Roadmap](docs/ROADMAP.md) | Planned phases and project progress |

## 🤝 Contributing

Contributions, bug reports and practical suggestions are welcome.

When reporting a bug, include:

- What you were trying to do.
- What you expected to happen.
- What actually happened.
- Steps to reproduce the issue.
- Relevant logs with credentials, tokens and personal data removed.

Please keep changes focused and explain how you tested them.

## 🔗 Related projects

- [Limbo](https://github.com/Quincunx33/limbo) — the Python framework used by the backend.
- [Bot Guard](https://github.com/Quincunx33/bot-guard-py) — request protection for Python applications.

---

<div align="center">

### ✦ NEXUS REMOTE

**One app. One website. One shared service.**

<sub>Built for transparent, permission-based Android device management.</sub>

</div>

<!--
  NEXUS REMOTE
  A cloud-hosted Android remote management service.
-->

<div align="center">

# ✦ NEXUS REMOTE

### Your Android devices. One secure place.

**A cloud-hosted Android remote management platform, powered by our own online service and Python backend.**

<p>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Backend-Python-3776AB?style=for-the-badge&logo=python&logoColor=white" alt="Python">
  <img src="https://img.shields.io/badge/Service-Cloud%20Hosted-4C8BF5?style=for-the-badge" alt="Cloud hosted">
  <img src="https://img.shields.io/badge/Status-Active%20Development-orange?style=for-the-badge" alt="Active development">
</p>

[Explore the project](https://github.com/Quincunx33/Control-xp) · [Read the roadmap](docs/ROADMAP.md) · [Report a bug](https://github.com/Quincunx33/Control-xp/issues)

</div>

---

## 👋 What is NEXUS REMOTE?

NEXUS REMOTE is a project for connecting and managing Android devices that **you own or have permission to manage**.

Think of it as a service we operate for you:

- 📱 **Android app** — register, sign in, pair devices and manage them directly from your phone.
- ☁️ **Our online service** — we host and maintain the Python backend on our own infrastructure.
- 🌐 **Our website** — supports its own registration and login, plus an optional browser dashboard. The same account works on the website and Android app.

**No personal VPS, Python hosting account or self-hosting setup should be required for normal users.**

> **Development note:** NEXUS REMOTE is actively being developed. Some parts may still be incomplete or change between versions. Check the roadmap before using it for anything important.

## ✨ What is included?

| Part | What it does |
|---|---|
| 📱 Android application | Native app built with Kotlin and Jetpack Compose |
| ☁️ Hosted Python backend | Operated by the NEXUS REMOTE team; users won't need to deploy it themselves |
| 🛡️ Bot Guard | Adds a protection layer around incoming requests |
| ⚡ Limbo | Provides the Python backend framework |
| 🔗 Device pairing | Designed to connect an account with an approved device |
| 📡 Live connection | WebSocket-based communication |
| 🌐 Web dashboard | Optional browser-based interface |

Feature availability can vary while development is in progress. The roadmap describes the intended project direction.

## 🧩 How does it work?

You don't need to understand all the code to understand the idea:

1. Register or sign in through either the Android app or official website.
2. Use the same account on both platforms; account and device data are stored centrally.
3. Connect an authorized device through the pairing process.
4. Manage devices from the Android app or website dashboard.

```text
       ANDROID APP
    Sign in · Pair · Manage
             |
          HTTPS/WSS
             |
             v
      OUR HOSTED BACKEND
        /          \
   BOT GUARD       LIMBO
   Protection    API/server
        \          /
          DATABASE
             |
      Device connection
```

## 🛠️ Built with

- **Android:** Kotlin, Jetpack Compose
- **Backend:** Python
- **Backend framework:** [Limbo](https://github.com/Quincunx33/limbo)
- **Request protection:** [Bot Guard](https://github.com/Quincunx33/bot-guard-py)
- **Database:** SQLite
- **Communication:** HTTP, WebSocket and WebRTC
- **Optional web interface:** HTML, CSS and Vanilla JavaScript

## 🧑‍💻 Developer setup (optional)

The steps below are for contributors and developers who want to run the backend locally. **Regular users will connect to the official NEXUS REMOTE service from the Android app; they will not need to install Python, run a server or self-host anything.**

## 🚀 Run the backend locally

### What you'll need

- Git
- Python (use a version supported by the project's dependencies)
- Android Studio, if you also want to build the Android app

### 1. Get the project

```bash
git clone https://github.com/Quincunx33/Control-xp.git
cd Control-xp
```

### 2. Create a virtual environment

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

### 3. Install the dependencies

```bash
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

### 4. Start the server

On Linux, macOS, or a shell with Bash available:

```bash
bash scripts/start_backend.sh
```

The startup script prepares the database and launches the backend. The default local address is:

```text
http://127.0.0.1:8000
```

**First time running it?** That's okay. If something goes wrong, check the error printed in your terminal and open an issue with the relevant log. Remove passwords, tokens and other private information before sharing logs.

## 📱 Build the Android app

1. Install [Android Studio](https://developer.android.com/studio).
2. Open the cloned project folder.
3. Wait for Gradle to sync.
4. Set the backend address for your environment.
5. Select an emulator or connected Android device and run the app.

You can also try a command-line debug build:

```bash
gradle :app:assembleDebug
```

A physical phone must be able to reach the backend over your network. The emulator address `10.0.2.2` is a special address for reaching the host computer from the standard Android emulator.

## 🌐 Do I need to host anything or use the website?

**No. The plan is for us to operate the service.**

- We will launch and maintain our own website and online backend.
- The Android app and official website will both connect to the same service.
- Users can register and log in from either the Android app or website.
- The same account and authorized device list will be available across both clients.
- Pairing and device management will be available in the app and website dashboard.
- Users won't need their own VPS, Python hosting account, domain or backend installation.

The service itself still needs an internet connection to communicate with registered devices.

## ☁️ What's coming next?

These are planned improvements, not promises that the features are already available.

### 1. Official NEXUS REMOTE online service
- Launch our own official website and hosted backend.
- Deploy and maintain the Python service ourselves.
- Provide secure account registration, login and device pairing from the Android app.
- Configure persistent storage, HTTPS, WebSocket support, monitoring, backups and service recovery.
- Make the official service the default connection target so ordinary users do not need to self-host or configure Python hosting.

### 2. Complete standalone Android experience
- Registration, login and account management inside the app.
- Device pairing and management without opening the website.
- Secure connection to our official hosted backend.
- Clearer pairing, connection and error screens.
- A smooth first-time setup for beginners.

### 3. Android app updates
- Show the installed app version.
- Notify users when a new release is available.
- Provide release notes and a safe update flow.
- Use official distribution or a user-confirmed installation process — never silent installation.

### 4. Advanced remote administration tools
- Expand the device command system for devices that have been enrolled and authorized.
- Remote screen viewing through Android's consent-based MediaProjection flow.
- User-approved file transfer with integrity checks.
- Device status, battery, storage and connectivity information.
- Additional remote support actions where Android permits them.
- Clear on-device indicators, permission controls, audit history and a device-side stop/revoke option.

These tools are intended for legitimate remote support and administration—not stealth access. Android security restrictions and user consent cannot be bypassed.

### 5. Stability and polish
- Better reconnect and offline handling.
- More useful device status information.
- More automated tests and clearer documentation.
- Improved performance and user experience.

## 🔐 Security matters

This project is intended only for devices you own or are explicitly authorized to manage.

- Device access should require authentication and authorization.
- Pairing should require approval and use temporary codes.
- Sensitive Android capabilities must follow Android's permission and consent flows.
- Screen capture must use Android's visible MediaProjection consent and foreground-service requirements.
- Never use this project to access someone else's device without permission.
- Never commit passwords, API keys, tokens or private signing keys.

Bot Guard is an application-level protection layer. It does not replace secure server configuration, a firewall or infrastructure-level DDoS protection.

## 📚 Project guides

- [Architecture](docs/ARCHITECTURE.md)
- [Android, Website and Backend Data Flow](docs/DATA_FLOW.md)
- [Deployment](docs/DEPLOYMENT.md)
- [Roadmap](docs/ROADMAP.md)

Browse the `docs/` directory for additional technical notes.

## 🤝 Want to help?

Contributions, bug reports and practical suggestions are welcome!

If you report a bug, please include:
- What you were trying to do.
- What you expected to happen.
- What actually happened.
- Steps to reproduce it.
- Relevant logs (with private information removed).

## 🔗 Related projects

- [Limbo](https://github.com/Quincunx33/limbo) — the Python framework used by the backend.
- [Bot Guard](https://github.com/Quincunx33/bot-guard-py) — request protection for Python applications.

---

<div align="center">

**NEXUS REMOTE**

*One app. Our service. Your authorized devices.*

<sub>Built for transparent, permission-based Android device management.</sub>

</div>

## Python backend API

The Android application and official website use the same Python backend API. All HTTP endpoints below use the `/api/v1/` prefix.

### API endpoint reference

| Category | Endpoints |
|---|---|
| Authentication | `/auth/register`, `/auth/login`, `/auth/logout`, `/auth/me`, `/auth/sessions` |
| Devices | `/devices`, `/devices/register`, `/devices/{id}`, `/devices/{id}/status` |
| Pairing | `/pairing/create`, `/pairing/verify`, `/pairing/approve`, `/pairing/reject` |
| Commands | `/commands/registry`, `/devices/{id}/commands`, `/commands/{id}` |
| File transfer | `/files/create`, `/files/upload/{id}`, `/files/device/{id}` |
| Audit and security | `/audit`, `/security/events` |
| Real-time | `/ws` |
| Health | `/health` |

For example, the full login endpoint is `POST /api/v1/auth/login`, and the device-list endpoint is `GET /api/v1/devices`. The WebSocket endpoint uses the same API version prefix: `/api/v1/ws`.

> **Implementation status:** This table documents the intended/current API surface for the project. Check the backend route implementation and deployment configuration before relying on an endpoint in a production client; an entry in this README alone does not guarantee that the route is already implemented or enabled.

### Kotlin API usage

A typical login request sends JSON to the backend:

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password"
}
```

After a successful login, the app uses the returned session token for authenticated requests. For example, to retrieve the signed-in user's devices:

```http
GET /api/v1/devices
Authorization: Bearer SESSION_TOKEN
```

The actual response fields and error format should follow the backend's response schema. The Android client should keep session tokens in secure app storage and must not log credentials or tokens.

### Client and server integration notes

- **WebSocket authentication:** authenticate the connection using the backend's documented WebSocket authentication message or handshake. Do not assume that an HTTP Bearer header is automatically available in every WebSocket client.
- **JSON contracts:** document request bodies, success responses, validation errors and status codes for every endpoint. Keep Kotlin DTOs aligned with those schemas.
- **Kotlin API repository:** centralize HTTP calls in an API client/repository layer; use typed DTOs, structured error handling, timeouts and token refresh/session-expiry handling where supported.
- **Python security audit:** verify authentication and authorization on every protected route, validate input, rate-limit sensitive endpoints, protect sessions, record security-relevant events and avoid leaking secrets in logs.
- **File upload authorization:** verify the authenticated account owns or is authorized to access the target device and upload session. Enforce file size/type limits, safe storage names and integrity checks.
- **Device ownership:** every device lookup, status request, command and file operation must enforce account ownership and explicit device authorization on the server—not only in the Android UI.
- **CORS:** allow only the official website origins that need browser API access. CORS is not authentication and does not replace server-side authorization.


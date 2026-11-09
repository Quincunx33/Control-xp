# NEXUS REMOTE

<p align="center">
  <strong>Secure Android Device Management — Without Depending on a Browser Dashboard</strong>
</p>

<p align="center">
  <em>Native Android · Python Backend · Limbo · Bot Guard · Real-Time Communication</em>
</p>

NEXUS REMOTE is a self-hostable platform for securely connecting and managing Android devices that you own or are explicitly authorized to manage. It combines a native Android agent with a Python backend, device pairing, live status updates, and permission-aware remote features.

The project is being developed around one important goal: **the Android app should be usable on its own. A web dashboard may be available as an optional management interface, but it should not be required for everyday registration, login, pairing, or device management.**

> **Project status:** Active development. Check the roadmap and release notes before relying on any feature in production. Planned features are identified separately from currently implemented functionality.

---

## Highlights

- **Native Android app** — Kotlin and Jetpack Compose.
- **Python-hosted backend** — self-host the server on a Python-capable hosting service or your own VPS.
- **Limbo backend** — uses the Limbo Python framework rather than introducing a separate web framework.
- **Bot Guard protection** — request inspection and pre-authentication protection before login routes.
- **Secure device pairing** — short-lived pairing codes and explicit approval on the Android device.
- **Live communication** — authenticated WebSocket connections for presence, status, and command events.
- **Permission-aware features** — Android system permissions and consent flows are respected.
- **Optional web dashboard** — a browser interface can complement the Android app; it is not intended to be a mandatory dependency.

## Architecture

```text
             ANDROID APP
        Login · Register · Pairing
        Device Status · Management
                  |
               HTTPS/WSS
                  |
                  v
          PYTHON-HOSTED SERVER
                  |
          +-------+--------+
          |                |
          v                v
       BOT GUARD          LIMBO
   Pre-auth security   Backend/API
          |                |
          +-------+--------+
                  |
                  v
              DATABASE
        Accounts · Devices · Sessions
                  |
          +-------+--------+
          |                |
          v                v
      Android Agent    Optional Web UI
```

The Android application communicates directly with the backend. The optional web interface is a separate client of the same backend APIs.

## Technology

| Component | Technology |
|---|---|
| Android application | Kotlin, Jetpack Compose |
| Backend runtime | Python |
| Backend framework | [Limbo](https://github.com/Quincunx33/limbo) |
| Request protection | [Bot Guard](https://github.com/Quincunx33/bot-guard-py) |
| Database | SQLite |
| API | HTTP/REST |
| Real-time events | WebSocket |
| Screen-sharing transport | WebRTC |
| Optional dashboard | HTML, CSS, Vanilla JavaScript |

## Getting Started

### Requirements

- Python 3.10 or newer (use the version required by the pinned dependencies).
- Git.
- Android Studio for building the Android application.
- A device or emulator running a supported Android version.

### Backend setup

Clone the repository:

```bash
git clone https://github.com/Quincunx33/Control-xp.git
cd Control-xp
```

Create and activate a virtual environment:

```bash
python3 -m venv .venv
source .venv/bin/activate
```

On Windows PowerShell:

```powershell
py -m venv .venv
.venv\Scripts\Activate.ps1
```

Install the pinned Python dependencies:

```bash
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

Start the backend:

```bash
bash scripts/start_backend.sh
```

The startup script applies database migrations and starts the Limbo server. Its default local address is:

```text
http://127.0.0.1:8000
```

For production, configure a domain, HTTPS, trusted reverse proxies, and the environment variables documented in the deployment guide. Do not expose an unencrypted development server directly to the public internet.

### Android app

1. Open the repository in Android Studio.
2. Allow Gradle to sync.
3. Configure the backend base URL for your environment.
4. Build and install the debug application.

You can also build from the command line:

```bash
gradle :app:assembleDebug
```

The emulator's `10.0.2.2` address is only suitable for reaching a backend running on the host machine. A physical Android device needs a reachable server address.

## Device Pairing

The intended pairing flow is:

1. Sign in to the Android app.
2. Start the device-pairing flow to request a temporary code.
3. Enter the code in an authorized client.
4. Confirm the pairing request on the Android device.
5. The backend links the device to the account after successful verification.

Pairing codes must be short-lived and single-use. Device access must remain revocable by the account owner.

## Security and Privacy

NEXUS REMOTE is designed for devices that you own or have explicit permission to manage.

- Bot Guard should inspect requests before login and other authentication handlers.
- Authentication and device authorization are separate checks.
- Every device command must be authorized by the backend.
- WebSocket connections must be authenticated independently.
- Screen sharing must use Android's MediaProjection consent flow and a visible foreground notification.
- Camera, microphone, accessibility, and other sensitive capabilities must never be activated covertly.
- Do not store passwords, access tokens, or private signing keys in source control.
- Configure trusted proxies explicitly; do not trust arbitrary forwarded-IP headers.

Bot Guard provides application-layer protection. It does not replace a firewall, Cloudflare WAF, or infrastructure-level DDoS protection.

## Hosting

The backend is intended to run as a persistent Python application. A suitable Python hosting provider must support:

- A long-running Python process.
- Persistent writable storage for SQLite, or an external database.
- WebSocket connections.
- Configurable environment variables.
- HTTPS or a trusted reverse proxy.

A serverless platform that terminates processes between requests or does not support persistent WebSockets is not automatically compatible. Review the hosting provider's limits before deployment.

See [Deployment Guide](docs/DEPLOYMENT.md) for the current VPS setup and deployment configuration.

## Roadmap — Upcoming Work

The following items describe the next development direction. They are planned work, not claims that the features are already released.

### 1. Python-hosting-ready deployment

- Make deployment straightforward on Python application hosting platforms.
- Add a clear production entry point and hosting configuration.
- Document environment variables, persistent storage, WebSocket support, and health checks.
- Keep VPS deployment supported.
- Provide a deployment profile that does not require a separately hosted static website.

### 2. Standalone Android experience — no web dashboard required

- Add registration directly inside the Android app.
- Add login, logout, and account recovery flows in the app.
- Allow users to register, sign in, pair, and manage their devices from Android.
- Make the web dashboard optional rather than a required step.
- Keep the same backend APIs and security rules for every client.

**Clarification:** “Without the web dashboard” means the user interface can be used entirely from the Android app. A hosted backend is still required for account authentication and remote communication; this does not mean remote access works without a network or server.

### 3. Android app updates

- Improve the app's update and release workflow.
- Show the installed version and available release information.
- Notify users when a newer version is available.
- Provide a safe update path through an official distribution channel or a user-confirmed APK installation flow.
- Publish release notes and compatibility information.
- Never silently install updates or bypass Android's package-installation protections.

### 4. Reliability and usability

- Improve reconnect behavior and offline-state handling.
- Improve pairing feedback and error messages.
- Improve device status refresh and background-work behavior within Android limits.
- Expand automated tests across the Android app and backend.
- Improve setup and troubleshooting documentation.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [API Reference](docs/API.md)
- [Database Design](docs/DATABASE.md)
- [Security](docs/SECURITY.md)
- [Android Permissions](docs/ANDROID_PERMISSIONS.md)
- [WebSocket Protocol](docs/WEBSOCKET.md)
- [Deployment](docs/DEPLOYMENT.md)
- [Development Guide](docs/DEVELOPMENT.md)
- [Roadmap](docs/ROADMAP.md)

## Development and Testing

Install the backend dependencies before running backend tests:

```bash
python -m pip install -r requirements.txt
python backend/tests/test_backend.py
```

Run Android unit tests:

```bash
gradle :app:testDebugUnitTest
```

Test results depend on the local environment and installed toolchains. Run the commands yourself and check their output before treating a build or test suite as passing.

## Contributing

Bug reports, improvements, and feature suggestions are welcome. Please include:

- What you expected to happen.
- What actually happened.
- Steps to reproduce the issue.
- Relevant logs with credentials and personal data removed.
- Device model and Android version, when reporting Android issues.

## License

See the repository's [LICENSE](LICENSE) file for licensing terms.

---

**NEXUS REMOTE** — manage your authorized Android devices from the app, with a self-hosted Python backend.

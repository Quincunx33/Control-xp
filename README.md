<!--
  NEXUS REMOTE
  A self-hosted Android device management project.
-->

<div align="center">

# ✦ NEXUS REMOTE

### Your Android devices. One secure place.

**A self-hosted device management project with a native Android app and a Python backend.**

<p>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Backend-Python-3776AB?style=for-the-badge&logo=python&logoColor=white" alt="Python">
  <img src="https://img.shields.io/badge/Framework-Limbo-6655CC?style=for-the-badge" alt="Limbo">
  <img src="https://img.shields.io/badge/Status-Active%20Development-orange?style=for-the-badge" alt="Active development">
</p>

[Explore the project](https://github.com/Quincunx33/Control-xp) · [Read the roadmap](docs/ROADMAP.md) · [Report a bug](https://github.com/Quincunx33/Control-xp/issues)

</div>

---

## 👋 What is NEXUS REMOTE?

NEXUS REMOTE is a project for connecting and managing Android devices that **you own or have permission to manage**.

Think of it as two parts working together:

- 📱 **Android app** — the place where you sign in, connect a device and manage it.
- 🖥️ **Python backend** — the server that handles accounts, device connections and communication.

The goal is simple: you should be able to use the Android app without having to open a website. A web dashboard can remain available as an optional extra.

> **Development note:** NEXUS REMOTE is actively being developed. Some parts may still be incomplete or change between versions. Check the roadmap before using it for anything important.

## ✨ What is included?

| Part | What it does |
|---|---|
| 📱 Android application | Native app built with Kotlin and Jetpack Compose |
| 🐍 Python backend | Runs the server and handles application requests |
| 🛡️ Bot Guard | Adds a protection layer around incoming requests |
| ⚡ Limbo | Provides the Python backend framework |
| 🔗 Device pairing | Designed to connect an account with an approved device |
| 📡 Live connection | WebSocket-based communication |
| 🌐 Web dashboard | Optional browser-based interface |

Feature availability can vary while development is in progress. The roadmap describes the intended project direction.

## 🧩 How does it work?

You don't need to understand all the code to understand the idea:

1. **Open the Android app** and sign in.
2. **Connect an authorized device** through the pairing process.
3. **The Python server** checks the account and handles communication.
4. **Manage your connected device** from the app. The web dashboard can be used as an additional interface.

```text
       ANDROID APP
    Sign in · Pair · Manage
             |
          HTTPS/WSS
             |
             v
       PYTHON BACKEND
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

## 🌐 Do I need the website?

**The long-term plan is: no.**

NEXUS REMOTE is moving toward a standalone Android experience:

- Create an account from the Android app.
- Sign in and manage your account in the app.
- Pair and manage devices without opening a browser.
- Keep the web dashboard as an optional alternative.

One important distinction: **not needing the website does not mean not needing a server.** Remote device management still needs a reachable backend and an internet or network connection.

## ☁️ What's coming next?

These are planned improvements, not promises that the features are already available.

### 1. Python-hosting-ready deployment
- Make setup easier on Python application hosting platforms.
- Provide a clear production start command and hosting configuration.
- Document environment variables, storage, health checks and WebSocket requirements.
- Keep self-hosting on a VPS as an option.

### 2. A complete standalone Android app
- Registration and login directly inside the app.
- Account and device management without the web dashboard.
- Clearer pairing, connection and error screens.
- A smoother first-time setup for beginners.

### 3. Android app updates
- Show the installed app version.
- Notify users when a new release is available.
- Provide release notes and a safe update flow.
- Use official distribution or a user-confirmed installation process — never silent installation.

### 4. Stability and polish
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

*One app. Your devices. Your server.*

<sub>Built for transparent, permission-based Android device management.</sub>

</div>

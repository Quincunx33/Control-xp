# NEXUS REMOTE — Development Guide

## 1. Project Directory Structure
```
nexus-remote/
├── requirements.txt            # Pinned Limbo and Bot Guard PyPI packages
├── app/                        # Android Native Agent (Kotlin, Compose)
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt
│   │   └── nexusremote/
│   │       ├── data/model/     # Moshi JSON data models
│   │       ├── security/       # Session & credential storage
│   │       ├── telemetry/      # Hardware status & sensors
│   │       ├── permissions/    # System permission inspection
│   │       ├── network/        # Retrofit & OkHttp WebSocket
│   │       ├── commands/       # Strict Command Registry Executor
│   │       ├── services/       # ScreenCaptureService foreground service
│   │       └── ui/             # Jetpack Compose MVVM UI & ViewModels
│   └── src/test/java/com/example/  # Robolectric & JUnit test suite
├── backend/                    # Self-hostable Limbo + Bot Guard backend
│   ├── nexus/                  # Backend application modules
│   │   ├── app.py              # Main Limbo application & route mount
│   │   ├── config.py           # Environment settings & secrets
│   │   ├── database/           # SQLite connection & migration runner
│   │   ├── security/           # Bot Guard, passwords, tokens, proxies
│   │   ├── auth/               # User registration, login, sessions
│   │   ├── devices/            # Device registry & presence state machine
│   │   ├── pairing/            # 6-character code verification workflow
│   │   ├── commands/           # Strict command execution engine
│   │   ├── websocket/          # Authenticated WebSocket Gateway
│   │   ├── files/              # Secure file storage
│   │   └── audit/              # Audit trail & security event logs
│   ├── server.py               # Executable server entrypoint
│   └── tests/                  # Automated Python test suite
├── web/                        # Pure Vanilla HTML, CSS, and JS Dashboard
│   ├── index.html              # Landing portal redirect
│   ├── login.html              # Operator authentication
│   ├── register.html           # Account registration
│   ├── dashboard.html          # Mission overview & fleet metrics
│   ├── devices.html            # Fleet registry & filtering
│   ├── device-details.html     # Dedicated control center (6 tabs)
│   ├── sessions.html           # Active session manager
│   ├── settings.html           # Platform configuration & health
│   └── assets/
│       ├── css/style.css       # Modern cybernetic stylesheet
│       └── js/                 # Modular API, WebSocket, and WebRTC clients
├── docs/                       # Complete architectural specifications
├── scripts/                    # Startup and reverse proxy configs
└── README.md                   # Master overview
```

## 2. Running the Backend Locally
```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
python3 backend/server.py --port 8000
```
Visit `http://localhost:8000/` in your browser.

## 3. Running Backend Tests
```bash
source .venv/bin/activate
python3 backend/tests/test_backend.py
```

## 4. Running Android Tests
```bash
gradle :app:testDebugUnitTest
```

## 5. Compiling Android Agent APK
```bash
gradle :app:assembleDebug
```

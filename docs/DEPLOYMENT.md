# NEXUS REMOTE — Production Deployment Guide

## 1. Prerequisites
- Linux VPS (Ubuntu 22.04 LTS or Debian 12 recommended)
- Python 3.10+
- Git (to clone the application repository)
- Nginx or Cloudflare Reverse Proxy
- Domain name with SSL certificates (Let's Encrypt / Certbot)

## 2. Backend Deployment

### Step 1: Clone Repository & Setup Directory
```bash
git clone <repository_url> /opt/nexus-remote
cd /opt/nexus-remote
mkdir -p backend/data backend/data/uploads
```

### Step 2: Configure Environment
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
nano .env
```
Ensure you generate a secure key:
```bash
python3 -c "import secrets; print(secrets.token_hex(32))"
```
Set `NEXUS_SECRET_KEY=<generated_key>`.

### Step 3: Run Migrations & Start Server
```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
bash scripts/start_backend.sh
```

### Step 4: Systemd Service Configuration
Create `/etc/systemd/system/nexus-remote.service`:
```ini
[Unit]
Description=NEXUS REMOTE Limbo Backend
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/nexus-remote
ExecStart=/opt/nexus-remote/.venv/bin/python /opt/nexus-remote/backend/server.py --host 127.0.0.1 --port 8000
Restart=always
RestartSec=3
EnvironmentFile=/opt/nexus-remote/.env

[Install]
WantedBy=multi-user.target
```
Enable and start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now nexus-remote
sudo systemctl status nexus-remote
```

### Step 5: Nginx Reverse Proxy
Copy `scripts/nginx_reverse_proxy.conf` to `/etc/nginx/sites-available/nexus-remote`:
```bash
sudo cp scripts/nginx_reverse_proxy.conf /etc/nginx/sites-available/nexus-remote
sudo ln -s /etc/nginx/sites-available/nexus-remote /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

## 3. Web Dashboard (Cloudflare Pages or Self-Hosted)
The Web Dashboard in `web/` consists of pure static HTML, CSS, and Vanilla JavaScript:
- **Cloudflare Pages**: Connect your Git repository, set the build output directory to `web`, and deploy.
- **Self-Hosted via Limbo**: The Limbo backend serves the dashboard directly from `/` out-of-the-box!

## 4. Android Agent Build & Distribution
1. Configure `serverUrl` in `SettingsTab` or set default in `SessionManager.kt`.
2. Generate APK:
```bash
gradle :app:assembleRelease
```
The signed APK will be output to `app/build/outputs/apk/release/app-release.apk`.

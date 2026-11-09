#!/usr/bin/env bash
set -e

# Change to workspace root
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=== Starting NEXUS REMOTE Limbo Backend ==="

# Check Python version
python3 --version

# Verify upstream packages are installed in the active Python environment
if ! python3 -c "import bot_guard, limbo" >/dev/null 2>&1; then
    echo "Backend dependencies are missing. Run: python3 -m pip install -r requirements.txt"
    exit 1
fi

# Run database schema migrations
echo "Applying database migrations..."
python3 -c "import sys; sys.path.insert(0, 'backend'); from nexus.database.migrations.runner import run_migrations; print('Migrations:', run_migrations())"

# Start Limbo HTTP & WebSocket server
echo "Starting Limbo Server on http://0.0.0.0:8000..."
exec python3 backend/server.py --host 0.0.0.0 --port 8000

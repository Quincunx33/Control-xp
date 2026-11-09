#!/usr/bin/env bash
set -e

# Change to workspace root
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=== Starting NEXUS REMOTE Limbo Backend ==="

# Check Python version
python3 --version

# Run database schema migrations
echo "Applying database migrations..."
python3 -c "import sys; sys.path.insert(0, 'backend'); sys.path.insert(0, 'backend/framework'); from nexus.database.migrations.runner import run_migrations; print('Migrations:', run_migrations())"

# Start Limbo HTTP & WebSocket server
echo "Starting Limbo Server on http://0.0.0.0:8000..."
exec python3 backend/server.py --host 0.0.0.0 --port 8000

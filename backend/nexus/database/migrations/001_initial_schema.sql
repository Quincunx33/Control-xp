-- NEXUS REMOTE Migration 001: Initial Normalized Schema

CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at REAL NOT NULL,
    updated_at REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    token TEXT UNIQUE NOT NULL,
    device_info TEXT,
    expires_at REAL NOT NULL,
    created_at REAL NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS devices (
    id TEXT PRIMARY KEY,
    user_id TEXT,
    name TEXT NOT NULL,
    model TEXT NOT NULL,
    android_version TEXT NOT NULL,
    app_version TEXT NOT NULL,
    screen_res TEXT,
    status TEXT NOT NULL DEFAULT 'UNREGISTERED',
    last_seen REAL,
    created_at REAL NOT NULL,
    updated_at REAL NOT NULL,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS device_credentials (
    device_id TEXT PRIMARY KEY,
    device_key_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    created_at REAL NOT NULL,
    FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS pairing_requests (
    id TEXT PRIMARY KEY,
    code TEXT UNIQUE NOT NULL,
    device_id TEXT NOT NULL,
    user_id TEXT,
    device_info TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    expires_at REAL NOT NULL,
    created_at REAL NOT NULL,
    FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_permissions (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    permission TEXT NOT NULL,
    granted INTEGER NOT NULL DEFAULT 0,
    updated_at REAL NOT NULL,
    UNIQUE(device_id, permission),
    FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_status (
    device_id TEXT PRIMARY KEY,
    battery_level INTEGER DEFAULT 100,
    charging INTEGER DEFAULT 0,
    battery_temp REAL,
    storage_free_mb INTEGER DEFAULT 0,
    storage_total_mb INTEGER DEFAULT 0,
    network_type TEXT DEFAULT 'WIFI',
    wifi_ssid TEXT,
    updated_at REAL NOT NULL,
    FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS commands (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    command_type TEXT NOT NULL,
    params_json TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at REAL NOT NULL,
    dispatched_at REAL,
    completed_at REAL,
    FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS command_results (
    command_id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    result_json TEXT,
    error_message TEXT,
    received_at REAL NOT NULL,
    FOREIGN KEY(command_id) REFERENCES commands(id) ON DELETE CASCADE,
    FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS streaming_sessions (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'REQUESTED',
    webrtc_state TEXT DEFAULT 'NEW',
    started_at REAL NOT NULL,
    ended_at REAL,
    FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS file_transfers (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    direction TEXT NOT NULL, -- UPLOAD, DOWNLOAD
    filename TEXT NOT NULL,
    filesize INTEGER NOT NULL DEFAULT 0,
    checksum TEXT,
    status TEXT NOT NULL DEFAULT 'REQUESTED', -- REQUESTED, APPROVED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    storage_path TEXT,
    created_at REAL NOT NULL,
    completed_at REAL,
    FOREIGN KEY(device_id) REFERENCES devices(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id TEXT PRIMARY KEY,
    user_id TEXT,
    device_id TEXT,
    action TEXT NOT NULL,
    details_json TEXT,
    client_ip TEXT,
    timestamp REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS security_events (
    id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL,
    severity TEXT NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    client_ip TEXT,
    user_agent TEXT,
    details_json TEXT,
    action_taken TEXT NOT NULL,
    timestamp REAL NOT NULL
);

-- Performance & Lookup Indexes
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(token);
CREATE INDEX IF NOT EXISTS idx_devices_user_id ON devices(user_id);
CREATE INDEX IF NOT EXISTS idx_devices_status ON devices(status);
CREATE INDEX IF NOT EXISTS idx_pairing_code ON pairing_requests(code);
CREATE INDEX IF NOT EXISTS idx_commands_device ON commands(device_id);
CREATE INDEX IF NOT EXISTS idx_commands_status ON commands(status);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_sec_events_time ON security_events(timestamp);

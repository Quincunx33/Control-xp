-- NEXUS REMOTE Migration 002: Advanced Telemetry (GPS Location and RAM Memory)

ALTER TABLE device_status ADD COLUMN latitude REAL;
ALTER TABLE device_status ADD COLUMN longitude REAL;
ALTER TABLE device_status ADD COLUMN accuracy REAL;
ALTER TABLE device_status ADD COLUMN ram_free_mb INTEGER;
ALTER TABLE device_status ADD COLUMN ram_total_mb INTEGER;

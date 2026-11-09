"""Strict Remote Command Registry with permission policies and schemas."""
from __future__ import annotations
from dataclasses import dataclass
from typing import Any, Optional

@dataclass(frozen=True)
class CommandDefinition:
    command_type: str
    description: str
    required_permissions: tuple[str, ...]
    timeout_seconds: int
    param_schema: dict[str, str]  # param_name -> type
    audit_level: str  # NORMAL, HIGH, SENSITIVE

COMMAND_REGISTRY: dict[str, CommandDefinition] = {
    "ping": CommandDefinition(
        command_type="ping",
        description="Check real-time network latency and device responsiveness",
        required_permissions=(),
        timeout_seconds=10,
        param_schema={},
        audit_level="NORMAL"
    ),
    "sync_status": CommandDefinition(
        command_type="sync_status",
        description="Refresh device telemetry including battery, storage, and network",
        required_permissions=(),
        timeout_seconds=15,
        param_schema={},
        audit_level="NORMAL"
    ),
    "battery_check": CommandDefinition(
        command_type="battery_check",
        description="Inspect detailed battery voltage, health, temperature, and charging speed",
        required_permissions=(),
        timeout_seconds=15,
        param_schema={},
        audit_level="NORMAL"
    ),
    "storage_info": CommandDefinition(
        command_type="storage_info",
        description="Query device storage capacity and filesystem statistics",
        required_permissions=(),
        timeout_seconds=15,
        param_schema={},
        audit_level="NORMAL"
    ),
    "vibrate_device": CommandDefinition(
        command_type="vibrate_device",
        description="Trigger device vibration alert pattern for physical locating",
        required_permissions=("android.permission.VIBRATE",),
        timeout_seconds=10,
        param_schema={"duration_ms": "int"},
        audit_level="NORMAL"
    ),
    "notification_alert": CommandDefinition(
        command_type="notification_alert",
        description="Display an administrative notification message on the device screen",
        required_permissions=("android.permission.POST_NOTIFICATIONS",),
        timeout_seconds=15,
        param_schema={"title": "str", "message": "str"},
        audit_level="HIGH"
    ),
    "request_screen_share": CommandDefinition(
        command_type="request_screen_share",
        description="Request user-approved MediaProjection screen sharing session",
        required_permissions=("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",),
        timeout_seconds=45,
        param_schema={"session_id": "str"},
        audit_level="SENSITIVE"
    ),
    "file_transfer_request": CommandDefinition(
        command_type="file_transfer_request",
        description="Request user-approved file transfer between device and web dashboard",
        required_permissions=(),
        timeout_seconds=30,
        param_schema={"transfer_id": "str", "filename": "str"},
        audit_level="SENSITIVE"
    ),
    "inject_touch": CommandDefinition(
        command_type="inject_touch",
        description="Simulate a touch click on device screen via Accessibility Service",
        required_permissions=("android.permission.BIND_ACCESSIBILITY_SERVICE",),
        timeout_seconds=10,
        param_schema={"x": "float", "y": "float"},
        audit_level="SENSITIVE"
    ),
    "inject_swipe": CommandDefinition(
        command_type="inject_swipe",
        description="Simulate a swipe gesture between two points",
        required_permissions=("android.permission.BIND_ACCESSIBILITY_SERVICE",),
        timeout_seconds=10,
        param_schema={"x1": "float", "y1": "float", "x2": "float", "y2": "float"},
        audit_level="SENSITIVE"
    ),
    "press_key": CommandDefinition(
        command_type="press_key",
        description="Execute global navigation action (Back, Home, Recents, Notifications)",
        required_permissions=("android.permission.BIND_ACCESSIBILITY_SERVICE",),
        timeout_seconds=10,
        param_schema={"key": "str"},
        audit_level="HIGH"
    ),
    "set_clipboard": CommandDefinition(
        command_type="set_clipboard",
        description="Set device clipboard text from dashboard",
        required_permissions=(),
        timeout_seconds=10,
        param_schema={"text": "str"},
        audit_level="NORMAL"
    ),
    "get_clipboard": CommandDefinition(
        command_type="get_clipboard",
        description="Read current clipboard text from device",
        required_permissions=(),
        timeout_seconds=10,
        param_schema={},
        audit_level="HIGH"
    ),
    "get_location": CommandDefinition(
        command_type="get_location",
        description="Query precise GPS and network coordinates",
        required_permissions=("android.permission.ACCESS_FINE_LOCATION",),
        timeout_seconds=15,
        param_schema={},
        audit_level="SENSITIVE"
    ),
    "play_siren": CommandDefinition(
        command_type="play_siren",
        description="Trigger maximum volume alarm siren and strobe camera flashlight",
        required_permissions=(),
        timeout_seconds=15,
        param_schema={},
        audit_level="HIGH"
    ),
    "stop_siren": CommandDefinition(
        command_type="stop_siren",
        description="Silence alarm siren and turn off flashlight strobe",
        required_permissions=(),
        timeout_seconds=10,
        param_schema={},
        audit_level="NORMAL"
    ),
    "list_apps": CommandDefinition(
        command_type="list_apps",
        description="Enumerate installed application packages and system apps",
        required_permissions=(),
        timeout_seconds=20,
        param_schema={},
        audit_level="NORMAL"
    ),
    "open_app": CommandDefinition(
        command_type="open_app",
        description="Launch an installed application package on device",
        required_permissions=(),
        timeout_seconds=15,
        param_schema={"package_name": "str"},
        audit_level="HIGH"
    ),
    "browse_files": CommandDefinition(
        command_type="browse_files",
        description="Browse device storage files and directories",
        required_permissions=(),
        timeout_seconds=20,
        param_schema={},
        audit_level="SENSITIVE"
    ),
    "system_diagnostics": CommandDefinition(
        command_type="system_diagnostics",
        description="Inspect real-time RAM memory, CPU cores, uptime, and security patch",
        required_permissions=(),
        timeout_seconds=15,
        param_schema={},
        audit_level="NORMAL"
    ),
    "capture_photo": CommandDefinition(
        command_type="capture_photo",
        description="Capture camera photo snapshot from device",
        required_permissions=("android.permission.CAMERA",),
        timeout_seconds=20,
        param_schema={},
        audit_level="SENSITIVE"
    ),
    "record_audio": CommandDefinition(
        command_type="record_audio",
        description="Record short audio clip sample from microphone",
        required_permissions=("android.permission.RECORD_AUDIO",),
        timeout_seconds=30,
        param_schema={"duration_sec": "int"},
        audit_level="SENSITIVE"
    ),
    "lock_screen_overlay": CommandDefinition(
        command_type="lock_screen_overlay",
        description="Display lost device overlay banner on screen",
        required_permissions=(),
        timeout_seconds=15,
        param_schema={"title": "str", "message": "str"},
        audit_level="HIGH"
    ),
    "geofence_check": CommandDefinition(
        command_type="geofence_check",
        description="Verify device location against GPS geofence radius",
        required_permissions=("android.permission.ACCESS_FINE_LOCATION",),
        timeout_seconds=15,
        param_schema={"center_lat": "float", "center_lon": "float", "radius_m": "float"},
        audit_level="NORMAL"
    ),
    "shell_exec": CommandDefinition(
        command_type="shell_exec",
        description="Execute system diagnostic shell command",
        required_permissions=(),
        timeout_seconds=15,
        param_schema={"command": "str"},
        audit_level="HIGH"
    ),
    "set_volume": CommandDefinition(
        command_type="set_volume",
        description="Set device audio volume level (0-100%)",
        required_permissions=(),
        timeout_seconds=10,
        param_schema={"volume_percent": "int"},
        audit_level="NORMAL"
    ),
    "set_brightness": CommandDefinition(
        command_type="set_brightness",
        description="Adjust screen display brightness level (0-100%)",
        required_permissions=(),
        timeout_seconds=10,
        param_schema={"brightness_percent": "int"},
        audit_level="NORMAL"
    ),
    "network_diagnostics": CommandDefinition(
        command_type="network_diagnostics",
        description="Inspect active network interfaces, local IPs, Wi-Fi speed, and gateway",
        required_permissions=(),
        timeout_seconds=15,
        param_schema={},
        audit_level="NORMAL"
    ),
    "security_health_check": CommandDefinition(
        command_type="security_health_check",
        description="Verify lock screen protection, storage encryption, and ADB status",
        required_permissions=(),
        timeout_seconds=15,
        param_schema={},
        audit_level="NORMAL"
    ),
    "thermal_alert_config": CommandDefinition(
        command_type="thermal_alert_config",
        description="Configure battery temperature thermal warning threshold in Celsius",
        required_permissions=(),
        timeout_seconds=10,
        param_schema={"threshold_c": "float"},
        audit_level="NORMAL"
    )
}

def validate_command(command_type: str, params: Optional[dict[str, Any]] = None) -> CommandDefinition:
    if command_type not in COMMAND_REGISTRY:
        raise ValueError(f"Unknown or unauthorized command type: '{command_type}'")
    defn = COMMAND_REGISTRY[command_type]
    params = params or {}
    for p_name, p_type in defn.param_schema.items():
        if p_name not in params:
            raise ValueError(f"Missing required parameter '{p_name}' for command '{command_type}'")
    return defn

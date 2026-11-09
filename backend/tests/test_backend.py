"""Comprehensive automated test suite for NEXUS REMOTE backend."""
from __future__ import annotations
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BACKEND_DIR))

# Use a clean temporary database for tests
_test_db = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
_test_db.close()
os.environ["NEXUS_DB_PATH"] = _test_db.name

from nexus.database.migrations.runner import run_migrations
from nexus.database.db import get_connection, fetch_one, fetch_all
from nexus.security.passwords import hash_password, verify_password
from nexus.security.tokens import create_token, decode_token, generate_pairing_code, generate_device_key
from nexus.security.trusted_proxy import resolve_client_ip
from nexus.security.bot_guard_integration import get_bot_guard
from nexus.auth.service import register_user, authenticate_user, verify_session, revoke_session
from nexus.devices.registry import (
    register_device, get_device, list_devices, rename_device,
    revoke_device, delete_device, update_device_status, update_device_permissions
)
from nexus.pairing.service import (
    create_pairing_request, verify_pairing_code, approve_pairing,
    reject_pairing, get_pairing_status
)
from nexus.commands.registry import COMMAND_REGISTRY, validate_command
from nexus.commands.service import (
    create_command, record_command_result, get_command_status, cancel_command
)
from nexus.files.service import create_file_transfer, save_uploaded_file, get_file_transfer
from nexus.audit.service import log_audit, get_recent_audit_logs, log_security_event, get_recent_security_events

class TestNexusRemote(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        run_migrations()

    @classmethod
    def tearDownClass(cls):
        try:
            os.unlink(_test_db.name)
        except Exception:
            pass

    def test_01_migrations_idempotency(self):
        applied = run_migrations()
        self.assertEqual(applied, [])
        tables = fetch_all("SELECT name FROM sqlite_master WHERE type='table';")
        table_names = {t["name"] for t in tables}
        self.assertIn("users", table_names)
        self.assertIn("devices", table_names)
        self.assertIn("commands", table_names)
        self.assertIn("pairing_requests", table_names)
        self.assertIn("audit_logs", table_names)
        self.assertIn("security_events", table_names)

    def test_02_password_hashing(self):
        pwd = "SecureP@ssw0rd123!"
        hashed = hash_password(pwd)
        self.assertTrue(hashed.startswith("pbkdf2:sha256:"))
        self.assertTrue(verify_password(pwd, hashed))
        self.assertFalse(verify_password("WrongPassword!", hashed))

    def test_03_token_generation_and_decoding(self):
        token = create_token({"sub": "usr_test123", "role": "admin"}, expires_in=3600)
        decoded = decode_token(token)
        self.assertIsNotNone(decoded)
        self.assertEqual(decoded["sub"], "usr_test123")
        self.assertEqual(decoded["role"], "admin")

        # Tampered token
        tampered = token[:-5] + "XXXXX"
        self.assertIsNone(decode_token(tampered))

    def test_04_auth_registration_and_login(self):
        email = f"operator_{int(time.time())}@nexus.local"
        user = register_user(email, "MasterPassword2026!", "Nexus Operator", client_ip="192.168.1.50")
        self.assertIsNotNone(user["id"])
        self.assertEqual(user["email"], email)

        # Duplicate email rejected
        with self.assertRaises(ValueError):
            register_user(email, "MasterPassword2026!", "Duplicate", client_ip="192.168.1.50")

        # Login
        auth_res = authenticate_user(email, "MasterPassword2026!", device_info="TestSuite/1.0", client_ip="192.168.1.50")
        self.assertIn("token", auth_res)
        token = auth_res["token"]

        # Session verification
        session = verify_session(token)
        self.assertIsNotNone(session)
        self.assertEqual(session["user"]["id"], user["id"])

        # Logout & revocation
        self.assertTrue(revoke_session(token))
        self.assertIsNone(verify_session(token))

    def test_05_device_registration_and_status(self):
        dev = register_device(
            name="Pixel 8 Pro",
            model="Pixel 8 Pro",
            android_version="14.0",
            app_version="1.0.0",
            screen_res="1344x2992"
        )
        self.assertIsNotNone(dev["device_id"])
        self.assertIsNotNone(dev["device_key"])
        self.assertEqual(dev["status"], "PAIRING_PENDING")

        # Update telemetry
        update_device_status(
            device_id=dev["device_id"],
            battery_level=87,
            charging=True,
            battery_temp=28.4,
            storage_free_mb=65400,
            storage_total_mb=128000,
            network_type="WIFI",
            wifi_ssid="NexusLab-Secure"
        )
        update_device_permissions(
            device_id=dev["device_id"],
            permissions={"android.permission.CAMERA": True, "android.permission.VIBRATE": True}
        )

        fetched = get_device(dev["device_id"])
        self.assertIsNotNone(fetched)
        self.assertEqual(fetched["telemetry"]["battery_level"], 87)
        self.assertEqual(fetched["telemetry"]["wifi_ssid"], "NexusLab-Secure")
        self.assertTrue(fetched["permissions"]["android.permission.VIBRATE"])

    def test_06_secure_device_pairing_workflow(self):
        # 1. Create user and device
        user = register_user(f"pair_{int(time.time())}@nexus.local", "SuperSecret99!", "Device Owner")
        dev = register_device(
            name="Galaxy Tab S9",
            model="SM-X710",
            android_version="14.0",
            app_version="1.0.0"
        )
        dev_id = dev["device_id"]

        # 2. Android app initiates pairing request
        pair_req = create_pairing_request(dev_id, device_info="Galaxy Tab S9 (Android 14)")
        code = pair_req["code"]
        self.assertEqual(len(code), 6)
        pairing_id = pair_req["pairing_id"]

        # 3. User on Web dashboard inputs code to verify
        verify_res = verify_pairing_code(code, user["id"])
        self.assertEqual(verify_res["status"], "VERIFIED")
        self.assertTrue(verify_res["requires_device_approval"])

        # 4. Device user explicitly approves
        approved = approve_pairing(pairing_id, dev_id)
        self.assertEqual(approved["status"], "PAIRED")
        self.assertEqual(approved["user_id"], user["id"])

        # 5. Device is now associated with user
        user_devices = list_devices(user["id"])
        dev_ids = [d["id"] for d in user_devices]
        self.assertIn(dev_id, dev_ids)

    def test_07_commands_pipeline_and_validation(self):
        user = register_user(f"cmd_{int(time.time())}@nexus.local", "CommandMaster123!", "Admin")
        dev = register_device("Test Rig", "Emulator", "14", "1.0", user_id=user["id"])
        dev_id = dev["device_id"]

        # Validate invalid command rejected
        with self.assertRaises(ValueError):
            validate_command("unauthorized_shell_command")

        # Validate valid command
        defn = validate_command("vibrate_device", {"duration_ms": 500})
        self.assertEqual(defn.command_type, "vibrate_device")

        # Create command
        cmd = create_command(dev_id, user["id"], "vibrate_device", {"duration_ms": 500})
        cmd_id = cmd["command_id"]

        # Receive result from device
        record_command_result(cmd_id, dev_id, result={"vibrated": True, "duration_ms": 500})
        status = get_command_status(cmd_id, user["id"])
        self.assertEqual(status["status"], "SUCCESS")
        self.assertEqual(status["result"]["vibrated"], True)

    def test_08_bot_guard_integration(self):
        guard = get_bot_guard()
        # Normal browser headers
        headers = {
            "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "accept-language": "en-US,en;q=0.9",
        }
        res = guard.inspect(headers, client_ip="203.0.113.1")
        self.assertIsNotNone(res)
        self.assertTrue(res.score < 0.6)

        # Scraper / Suspicious bot headers
        bot_headers = {
            "user-agent": "python-requests/2.28.1",
        }
        bot_res = guard.inspect(bot_headers, client_ip="198.51.100.1")
        self.assertIsNotNone(bot_res)
        self.assertTrue(bot_res.score > 0.5)

    def test_09_trusted_proxy_resolution(self):
        # Peer IP is not a trusted proxy -> returns peer IP
        ip1 = resolve_client_ip({"x-forwarded-for": "1.2.3.4"}, peer_ip="198.51.100.5")
        self.assertEqual(ip1, "198.51.100.5")

        # Peer IP is trusted loopback proxy -> extracts CF-Connecting-IP
        ip2 = resolve_client_ip({"cf-connecting-ip": "203.0.113.88"}, peer_ip="127.0.0.1")
        self.assertEqual(ip2, "203.0.113.88")

    def test_10_file_transfer_service(self):
        user = register_user(f"file_{int(time.time())}@nexus.local", "FileMaster123!", "File Operator")
        dev = register_device("File Device", "Model X", "14", "1.0", user_id=user["id"])
        transfer = create_file_transfer(dev["device_id"], user["id"], "test_report.pdf", "UPLOAD")
        t_id = transfer["transfer_id"]
        self.assertEqual(transfer["status"], "REQUESTED")

        content = b"NEXUS REMOTE ENCRYPTED PAYLOAD TEST BYTES"
        path = save_uploaded_file(t_id, content)
        self.assertTrue(os.path.exists(path))
        saved = get_file_transfer(t_id, user["id"])
        self.assertEqual(saved["status"], "COMPLETED")
        self.assertEqual(saved["filesize"], len(content))

if __name__ == "__main__":
    unittest.main()

# NEXUS REMOTE — Security Architecture & Bot Guard Integration

## 1. Zero-Trust Security Philosophy
NEXUS REMOTE strictly implements defense-in-depth:
1. **No covert monitoring**: All features (camera, screen sharing, file access) require explicit, official Android framework dialogs.
2. **Object-level backend authorization**: Users cannot access or control another user's device by changing IDs in requests.
3. **No plaintext credentials**: Passwords use PBKDF2-HMAC-SHA256 with 200,000 iterations and random salt. Device keys are salted and hashed.
4. **Immediate revocation**: Revoking device access deletes device credentials from the database and closes active WebSocket streams.

## 2. Bot Guard Pre-Authentication Pipeline
Bot Guard (`bot-guard-py`) executes **before** router dispatch and authentication handlers:

```
Incoming Request
       |
       v
Cloudflare / Reverse Proxy
       |
       v
Limbo HTTP Server
       |
       v
BOT GUARD (Pre-Auth)
       |
   +---+---+
   |       |
Allowed  Suspicious (Blocked/Throttled)
   |       |
   v       v
Router   Security Event Logged
   |
   v
Authentication & Authorization
```

- **Trusted Proxy Resolution**: Client IP extraction inspects `CF-Connecting-IP` or `X-Forwarded-For` only when the immediate peer IP belongs to a pre-configured trusted proxy subnet.
- **Brute-Force Flood Protection**: Token bucket rate limiters protect `/api/v1/auth/login` (10/min) and `/api/v1/pairing/*` (20/min).
- **Behavioral Scoring**: Inspects user agent signatures, CLI tools, headless browser markers, and header anomalies.
- **Modes**: Configurable between `shadow` (observability telemetry) and `enforce` (blocking requests exceeding policy threshold).

## 3. Pairing Security
- Pairing codes are 6-character unambiguous alphanumeric strings (`23456789ABCDEFGHJKLMNPQRSTUVWXYZ`).
- Codes expire automatically after 300 seconds (5 minutes).
- Entering the code in the Web Dashboard marks it `VERIFIED`, but association is **not created** until the physical device user taps **Approve** on the Android screen.

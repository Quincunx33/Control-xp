# NEXUS REMOTE — Kotlin, Website and Backend Data Flow

## 1. The three parts

1. **Android app:** Kotlin + Jetpack Compose. Owns the native UI, device identity, Android permissions, approved local device operations and local session handling.
2. **Official website:** HTML + CSS + Vanilla JavaScript. HTML provides structure, CSS appearance, and JavaScript handles forms, API requests, live events and DOM updates.
3. **Hosted backend:** Python/Limbo + Bot Guard + database. It is the shared authority for accounts, sessions, device ownership, pairing, permissions, commands and audit logs.

The website and Android app are two clients of the same service. Users can register or log in on either one and use the same account. Each client maintains its own session.

## 2. High-level architecture

~~~text
       ANDROID APP                                  OFFICIAL WEBSITE
   Kotlin / Jetpack Compose                     HTML + CSS + JavaScript
       |       ^                                      |       ^
       | JSON  | JSON                                 | JSON  | JSON
       | HTTPS | HTTPS                                | HTTPS | HTTPS
       v       |                                      v       |
       +-------+-------------+        +---------------+-------+
                             |        |
                       HOSTED BACKEND (Python / Limbo)
                       Auth · API · Authorization
                       Bot Guard · Device Registry
                       Database · Audit Log
                             |
                    Authenticated WebSocket
                      /                  \
              Android client         Browser client
                      \                  /
                       Device events and results
~~~

## 3. Registration and login from either client

### Android
1. User fills the form in a native Compose screen.
2. A Kotlin ViewModel validates basic input and calls an API repository.
3. The repository sends JSON over HTTPS to the official backend.
4. The backend validates the request, applies rate limits and Bot Guard checks, hashes passwords server-side, and creates/authenticates the account.
5. The backend returns a session response.
6. Kotlin stores session material using secure Android storage and updates Compose state.

### Website
1. User fills the HTML form.
2. Website JavaScript sends JSON to the same backend endpoint.
3. The backend performs the same validation, rate limiting, password hashing and authentication.
4. The browser receives the session response and stores it using a secure browser session strategy. Prefer Secure, HttpOnly, SameSite cookies where deployment supports them; avoid exposing long-lived secrets to JavaScript.
5. The website loads account and device data from the authenticated API.

Both clients use one account database and one authorization policy. Registering on the website must allow login in the Android app, and vice versa.

## 4. Normal Kotlin request/response path

~~~text
Compose Screen
   ↓ user action
ViewModel (coroutine)
   ↓
Repository / API client
   ↓ HTTPS + Authorization + JSON
Hosted Backend
   ↓ authenticate + authorize + query
Database
   ↓ JSON response
Repository maps response to Kotlin data classes
   ↓
StateFlow updates
   ↓
Compose recomposes the device list
~~~

Recommended Android layers:
- **API service:** endpoint definitions and transport.
- **DTO/data classes:** typed request and response models.
- **Repository:** hides networking details from UI.
- **ViewModel:** calls repository from coroutines and exposes StateFlow UI state.
- **Compose UI:** renders state and sends user actions to the ViewModel.

Do not put API administrative secrets in the APK. Treat the APK as a public client.

## 5. How website HTML changes when data arrives

The backend should return structured JSON—not executable HTML or JavaScript.

Example response:

~~~json
{
  "devices": [
    {
      "id": "device_123",
      "display_name": "My Android",
      "status": "online",
      "battery_percent": 82
    }
  ]
}
~~~

Website JavaScript reads the response and updates relevant DOM nodes. Use safe text APIs such as textContent for device names and other user-controlled values. Do not insert untrusted values through innerHTML, and never evaluate backend-provided JavaScript.

The HTML source file itself does not need to be rewritten every time a device changes status. The browser updates the page's live DOM from validated data. If a future feature lets an authorized user edit an HTML template, it should be a separate content-management feature with validation, preview, version history, authorization and rollback.

## 6. Real-time data in both directions

Use HTTPS REST endpoints for request/response operations:
- registration, login and logout;
- loading account profile and device lists;
- pairing and revoking a device;
- requesting an approved action;
- retrieving command results and audit history.

Use authenticated WebSocket messages for real-time events:
- device online/offline presence;
- telemetry updates;
- command accepted, progress and completion;
- screen-sharing signaling events;
- session expiry or revocation notifications.

Typical command lifecycle:

~~~text
User action in Android or Website
          ↓
HTTPS request to backend
          ↓
Backend authenticates user and checks device authorization
          ↓
Command is stored and routed to the paired Android agent
          ↓
Android agent checks local permission/consent requirements
          ↓
Agent performs only the allowed operation
          ↓
Result travels back through authenticated channel
          ↓
Backend records audit/result and broadcasts an event
          ↓
Android UI and website UI refresh their state
~~~

A WebSocket connection is not proof of authorization. Authenticate the connection and authorize every command against the account, device, action and current policy.

## 7. Kotlin and HTML inside one Android screen: optional WebView bridge

The current app is native Jetpack Compose; the website runs separately in a browser. A WebView bridge is therefore not required for ordinary operation.

If a future feature intentionally embeds a trusted web editor or dashboard in Android:
- JavaScript sends a structured JSON message to a documented Kotlin interface.
- Kotlin validates message size, schema, allowed action and trusted page origin.
- Kotlin returns a structured result through a controlled callback.
- Sensitive actions remain in Kotlin and backend authorization, with user confirmation as needed.

Do not expose broad Android objects, filesystem access, tokens or privileged methods through addJavascriptInterface. Do not evaluate arbitrary HTML/JavaScript received from the network. Restrict WebView to the official HTTPS origin and disable unnecessary capabilities.

## 8. Security and reliability requirements

- HTTPS only in production; WSS for WebSocket.
- Server-side password hashing, rate limiting, session expiry and revocation.
- Per-request account and device authorization; never trust client-supplied owner IDs.
- Validate JSON types, lengths, enums and payload sizes on both client and server.
- Store Android session material securely; do not hardcode secrets in the APK.
- Prefer secure HttpOnly cookies for browser sessions where feasible; protect cookie-authenticated state-changing requests against CSRF.
- Use short-lived credentials and revoke sessions on logout or suspicious activity.
- Audit account, pairing, permission and remote-action events without logging passwords or raw tokens.
- Handle offline mode, reconnect, duplicate events, retries and stale data explicitly.
- Android operations requiring user permission must use official Android flows and clear device-side indicators.

## 9. Implementation checklist

- [ ] Define and version the shared API contract.
- [ ] Build Kotlin API client, DTOs, repository and ViewModel state.
- [ ] Make website JavaScript call the same API contract.
- [ ] Standardize JSON error responses and validation.
- [ ] Define authenticated WebSocket event schemas and reconnect recovery.
- [ ] Test registration on one client and login on the other.
- [ ] Test that device lists and permitted changes stay consistent across clients.
- [ ] Add integration tests for authorization, revocation, session expiry and live events.
- [ ] Add WebView bridge only if an embedded web surface is actually required.

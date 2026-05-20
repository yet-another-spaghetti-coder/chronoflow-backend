# Backend WebSocket Origin Fix Required

## Problem
After migrating the frontend to a new Firebase Hosting deployment (`chronoflow-1.web.app`), the WebSocket connection from the frontend to the backend is failing.

## Error Observed
```
Refused to connect to wss://api.chronoflow.site/ws?userId=20468527492352491531
because it does not appear in the connect-src directive of the Content Security Policy.

SecurityError: The operation is insecure.
```

The browser shows "Unexpected Application Error" and the React app crashes.

## Frontend Fix (already applied)
Added `wss://api.chronoflow.site` to the `connect-src` CSP directive in `firebase.json`. This allows the browser to attempt the WebSocket connection.

## Backend Fix Needed
The backend WebSocket server at `wss://api.chronoflow.site/ws` likely validates the `Origin` header on incoming WebSocket upgrade requests. The new origin `https://chronoflow-1.web.app` must be allowed.

Check and update:
1. **WebSocket upgrade handler** — if the server checks the `Origin` header during the HTTP→WebSocket upgrade handshake, add `https://chronoflow-1.web.app` to the allowed origins list.
2. **CORS middleware** — if a CORS middleware runs before the WebSocket upgrade, ensure it allows the new origin for the `/ws` endpoint as well.
3. **Reverse proxy / load balancer** — if there is a proxy (e.g. nginx, Cloud Run, API Gateway) in front of the backend, check that it allows WebSocket upgrades (`Connection: Upgrade`, `Upgrade: websocket`) from the new origin.

## Origins to Allow
- `https://chronoflow-1.web.app` (primary)
- `https://chronoflow-1.firebaseapp.com` (alternate Firebase domain)
- `http://localhost:5173` (local dev, if not already allowed)

## Context
- Frontend GCP project changed from `chronoflow-474410` to `chronoflow-1`
- The WebSocket endpoint is `wss://api.chronoflow.site/ws?userId=<id>`
- The frontend code has not changed — only the hosting origin changed
- The CORS preflight fix (for REST API endpoints) has already been applied separately
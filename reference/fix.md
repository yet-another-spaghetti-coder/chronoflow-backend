# WebSocket Origin Fix — Summary ✅

**Status: FIXED** — verified in codebase

## Problem
After migrating the frontend from Firebase project `chronoflow-474410` to `chronoflow-1`, WebSocket connections to `wss://api.chronoflow.site/ws` were blocked due to the new origin (`https://chronoflow-1.web.app`) not being in the allowed origins list.

## What Was Fixed

### 1. Custom CORS Filter
**File:** `gateway/src/main/java/nus/edu/u/gateway/filter/CorsResponseFilter.java`

Added the following origins to the allowed origins list (lines 45–46):
- `https://chronoflow-1.web.app`
- `https://chronoflow-1.firebaseapp.com`

### 2. Spring Cloud Gateway CORS Config (Production)
**File:** `gateway/src/main/resources/application-prod.yaml`

Added the same origins to the Spring Cloud Gateway CORS allowed-origins configuration (lines 22–23):
- `https://chronoflow-1.web.app`
- `https://chronoflow-1.firebaseapp.com`

### 3. Dev Config
**File:** `gateway/src/main/resources/application-dev.yaml`

No changes needed — already uses `allowed-origin-patterns: "*"` (line 18) and `http://localhost:5173` (line 17).

## WebSocket Routing (No Changes Needed)
The gateway's `ws-upgrade` route forwards WebSocket upgrade requests to the `ws-gateway` service. The `wsgateway` WebSocketConfig does not enforce origin restrictions, so no changes were required there.

## Note
There is dual CORS handling (custom `CorsResponseFilter` + Spring Cloud Gateway built-in CORS). Both are now consistent. If duplicate `Access-Control-Allow-Origin` headers appear in responses, consider disabling one layer.

---

# PubSub Startup Failure — google-auth-library Version Conflict ✅

**Status: FIXED** — verified in codebase

## Problem
`user-service` and `attendee-service` fail to start with:
```
ClassNotFoundException: com.google.auth.mtls.CertificateSourceUnavailableException
```
The bean `publisherTransportChannelProvider` (from `GcpPubSubAutoConfiguration`) cannot be created.

## Root Cause
A dependency version conflict between `google-auth-library-oauth2-http` consumers:

| Dependency | Pulls in google-auth version | Needs |
|---|---|---|
| `firebase-admin:9.3.0` (user-service) | `1.23.0` | — |
| `gax-grpc:2.74.0` (from `spring-cloud-gcp-starter-pubsub:7.4.2`) | — | `>= 1.35.0` (has `CertificateSourceUnavailableException`) |

Maven resolves `google-auth-library-oauth2-http` to `1.23.0` (from firebase-admin) because it appears closer in the dependency tree. This old version is missing the `com.google.auth.mtls.CertificateSourceUnavailableException` class that `gax-grpc:2.74.0` expects at runtime.

For `attendee-service`, the same conflict applies — even though it doesn't declare `firebase-admin` directly, the `google-auth:1.23.0` gets pulled transitively and overrides what `gax-grpc` needs.

## Fix Applied
Added `google-auth-library` version management in the **parent pom.xml** `<dependencyManagement>` section to force version `1.39.0` globally.

**Changes made to `pom.xml` (parent):**
1. Added property `<google-auth.version>1.39.0</google-auth.version>` (line 76)
2. Added `<dependencyManagement>` entries for (lines 436–445):
   - `com.google.auth:google-auth-library-oauth2-http:${google-auth.version}`
   - `com.google.auth:google-auth-library-credentials:${google-auth.version}`

**Changes made to `user-service/pom.xml`:**
- Added `<exclusions>` on `firebase-admin:9.3.0` for both `google-auth-library-oauth2-http` and `google-auth-library-credentials` (lines 57–66)

**Changes made to `notification-service/pom.xml`:**
- Added exclusion for `google-auth-library-credentials` on `firebase-admin` (lines 59–62)
- Added explicit `google-auth-library-oauth2-http` dependency (lines 67–70) to inherit managed version

---

# Redis AUTH Failure on Local Dev — attendee-service ✅

**Status: FIXED** — verified in codebase

## Problem
`attendee-service` fails to start locally with:
```
ERR AUTH <password> called without any password configured for the default user.
```

## Root Cause
`attendee-service/src/main/resources/application-dev.yaml` had `password: ${DEV_REDIS_PASSWORD}` **uncommented** (line 52), while all other services had it commented out (`# password: ${DEV_REDIS_PASSWORD}`). This caused Redisson to send an AUTH command to the local Redis which has no password set.

## Fix Applied
Commented out the password line in `attendee-service/src/main/resources/application-dev.yaml` (line 52):
```yaml
# password: ${DEV_REDIS_PASSWORD}
```
This makes it consistent with all other services' dev configs (user-service, notification-service, event-service, task-service, file-service, gateway all have it commented out).

---

# PubSub Subscription Not Found — notification-service ⏳

**Status: NOT FIXED** — requires GCP infrastructure change (not a code fix)

## Problem
`notification-service` fails at runtime with:
```
com.google.api.gax.rpc.NotFoundException: io.grpc.StatusRuntimeException: NOT_FOUND: Resource not found (resource=chronoflow-notification-sub).
```

## Root Cause
The GCP Pub/Sub subscription `chronoflow-notification-sub` does not exist in the target GCP project. The `notification-service` subscriber (`NotificationEventSubscriber.java`, line 19) references this subscription, but it hasn't been provisioned in GCP.

Publishers (`attendee-service` and `task-service`) publish to topic `chronoflow-notification`. The subscription `chronoflow-notification-sub` must exist and be attached to that topic.

This likely happened because the GCP project changed (migration from `chronoflow-474410` to `chronoflow-1`) and the Pub/Sub resources were not recreated in the new project.

## Code References
- **Subscriber:** `notification-service/src/main/java/nus/edu/u/subscriber/NotificationEventSubscriber.java` — line 19: `SUBSCRIPTION = "chronoflow-notification-sub"`
- **Publishers:**
  - `attendee-service/src/main/java/nus/edu/u/attendee/publisher/NotificationPublisher.java` — line 22: `TOPIC_NAME = "chronoflow-notification"`
  - `task-service/src/main/java/nus/edu/u/task/publisher/NotificationPublisher.java` — line 22: `TOPIC_NAME = "chronoflow-notification"`
- **GCP project config:** `notification-service/src/main/resources/application.yaml` — line 40: `project-id: ${GCP_PROJECT_ID}`

## Fix Required
Create the Pub/Sub topic and subscription in the GCP project:
```bash
# 1. Create the topic (if it doesn't exist)
gcloud pubsub topics create chronoflow-notification --project=<GCP_PROJECT_ID>

# 2. Create the subscription attached to that topic
gcloud pubsub subscriptions create chronoflow-notification-sub \
  --topic=chronoflow-notification \
  --project=<GCP_PROJECT_ID>
```

Replace `<GCP_PROJECT_ID>` with the actual project ID (e.g., `chronoflow-1`).

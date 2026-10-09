# Hosted deployment preparation

Deployment is pending source-repository connection. No live URL has been verified.

## Railway backend

Connect the repository and configure service root directory `/backend`. The
Dockerfile builds with Java 21 and runs Maven verification before producing a
non-root runtime image. A failed build must be fixed before deployment is reported
successful. Docker/Maven verification has not been executed in the current workspace.

Provision PostgreSQL and enter these runtime variables through the hosting secret
manager; never commit their values:

- `SPRING_PROFILES_ACTIVE=demo,cloud` for the explicitly synthetic development demo.
- `RAILYATRA_DB_URL`: JDBC PostgreSQL URL using the database's private host and port.
- `RAILYATRA_DB_USERNAME` and `RAILYATRA_DB_PASSWORD`: database credentials.
- `RAILYATRA_JWT_SECRET`: a cryptographically random secret of at least 32 bytes.
- `RAILYATRA_DEMO_ADMIN_PASSWORD` and `RAILYATRA_DEMO_USER_PASSWORD`: generated unique
  passwords of at least 12 characters. These bootstrap only the reserved demo accounts.
- `RAILYATRA_CORS_ALLOWED_ORIGINS`: exact hosted frontend origin, no wildcard.
- `RAILYATRA_TICKET_VERIFICATION_BASE_URL`: deployed frontend URL followed by
  `/pnr?reference=` (Spring relaxed binding maps it to the ticket URL property).

The `cloud` profile binds to all interfaces on Railway's `PORT`, requires the
database environment variables, and enables Secure refresh cookies. PostgreSQL
remains persistent; synthetic import runs once using the existing database marker.
Payments remain simulated. This is not an official railway reservation.

## ChatGPT Sites frontend

Sites can host the React frontend but cannot run the Java backend. Route `/api/*`
through a same-origin Worker proxy to the verified Railway backend and build the
frontend with `VITE_API_BASE_URL` set to the empty string. Preserve streaming SSE,
Authorization and idempotency headers, and HttpOnly Set-Cookie responses. Forward
only to the fixed configured backend origin. Do not publish the current localhost
API fallback as a working hosted app.

The proxy is required for the existing Strict refresh cookie to remain same-origin;
cross-site direct API calls would break session refresh. Keep the backend's exact
CORS allowlist and existing server-side authentication and role checks.

After a successful cloud build, verify health, login/refresh, station search,
train search, admin authorization, train editing and the persisted booking flow
against the deployed endpoints. Then publish the frontend and report its live URL.

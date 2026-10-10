# RailYatra

RailYatra is an independent railway-reservation platform using local development data. Synthetic development data is explicitly marked `SYNTHETIC` and must not be treated as official/current Indian Railways information.

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 20+

## Run backend (Windows PowerShell)

```powershell
cd .\railyatra\backend
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.7-hotspot"
$env:RAILYATRA_JWT_SECRET = "replace-this-with-a-unique-local-development-secret-at-least-32-characters"
mvn spring-boot:run
```

Backend: `http://localhost:8080`

## Run frontend (Windows PowerShell)

```powershell
cd .\railyatra\frontend
npm install
npm run dev
```

Frontend: `http://localhost:5173`

## Verify

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Expected response:

```json
{ "status": "UP", "application": "RailYatra" }
```

## Current modules

- Station autocomplete, intermediate-stop train search, route timeline, lazy date-specific availability, and SSE availability updates.
- JWT/BCrypt authentication with rotating HTTP-only refresh sessions.
- Passenger form with server-side validation (maximum six) and user-scoped saved passengers.
- Reusable coach-layout templates and transactional seat-allocation foundation.
- Temporary seat holds with configurable automatic expiry and a checkout countdown.
- Transactional booking confirmation with authoritative server-side fare calculation, internal `RY…` references, simulated-payment records, idempotency keys, and physical seat assignment.

## Passenger APIs

All require authentication:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/passengers/validate` | Validate one to six passengers before a future booking operation |
| `GET` | `/api/saved-passengers` | List the caller's saved passengers |
| `POST` | `/api/saved-passengers` | Save a passenger |
| `PUT` | `/api/saved-passengers/{id}` | Update the caller's passenger |
| `DELETE` | `/api/saved-passengers/{id}` | Delete the caller's passenger |

## Coach and seat architecture

Flyway migrations `V8` and `V9` add reusable layout templates, their ordered slots, and date-specific seat allocations. Templates cover `SL`, `3A`, `2A`, `1A`, `CC`, `EC`, and `2S`; a new coach is materialized from a template rather than retyping its static seat layout. Examples supported by coach codes include `S1–S3`, `B1–B3`, `A1–A2`, `H1`, and `C1–C2`.

Seat types: `LOWER`, `MIDDLE`, `UPPER`, `SIDE_LOWER`, `SIDE_UPPER`, `WINDOW`, `AISLE`, and `CHAIR`.

The allocation service locks the date/class/quota inventory row pessimistically, checks authoritative capacity inside the transaction, selects a preferred matching berth when available, falls back to another valid free seat when it is not, persists a unique seat allocation, and only then decrements confirmed inventory. A database uniqueness constraint provides a second guard against assigning the same seat twice.

## Booking concurrency guarantees

RailYatra uses the database as the concurrency authority, so the same behavior works when the application runs on multiple backend instances. Hold creation and seat allocation are `@Transactional` and acquire a pessimistic write lock on the relevant date/class/quota inventory row. `SeatInventory` also has a JPA version column, while database constraints prevent a negative availability count, duplicate `(inventory, seat)` assignments, duplicate allocation-passenger rows, and duplicate user idempotency keys.

`BookingConcurrencyIT` starts simultaneous service calls for a single available unit. It proves that exactly one hold succeeds and inventory ends at zero, and that exactly one seat allocation persists when two allocators race for the same physical seat.

## Temporary seat holds

`POST /api/seat-holds` is authenticated and requires an `Idempotency-Key` header. It validates all passengers, locks the authoritative date/class/quota inventory row, reserves the requested capacity, and creates an `ACTIVE` hold atomically. The `GET /api/seat-holds/{id}` endpoint returns the authoritative state and expiry time; `DELETE /api/seat-holds/{id}` releases an active hold early.

Holds have `ACTIVE`, `CONSUMED`, `EXPIRED`, and `RELEASED` statuses. A scheduled expiry job releases capacity for expired active holds. Booking confirmation locks and revalidates the active hold before consuming it, so an expired hold can never become a confirmed booking.

## Booking API

`POST /api/bookings` requires a bearer token and an `Idempotency-Key` header. It accepts the hold ID, origin/destination station codes, one to six passengers, and a development-only simulated payment result. The backend validates the active user, hold, route and passenger data; calculates the fare from database fare rules; locks the inventory while assigning seats; creates a booking; consumes the hold; and emits an availability change only after commit.

Bookings keep `internalBookingReference`, `providerPnr`, and `providerType` separate. Local development bookings use `providerType: LOCAL` and never claim to be an official Indian Railways PNR. Supported booking statuses are `PENDING_PAYMENT`, `CONFIRMED`, `RAC`, `WAITLISTED`, `CANCELLED`, and `FAILED`; this confirmed-seat checkout currently produces `CONFIRMED` or `FAILED` for the simulated payment path.

## Internal reference / PNR status API

`GET /api/pnr/{reference}` looks up an internal RailYatra booking reference and returns the journey plus each passenger's current status. The response explicitly labels a `LOCAL` booking as a **RailYatra internal booking reference, not an official Indian Railways PNR**. The references start with `RY`, are generated on the server with approximately 100 bits of cryptographic randomness, and are protected by a database uniqueness constraint/index. Public lookup is rate-limited by client address (default: 10 requests per 60 seconds) and passenger names are masked.

## Reservation status engine

The booking engine assigns `CONFIRMED` first, then `RAC`, then `WAITLISTED`. It stores immutable `bookingStatus`/position separately from current status/position, so a response can accurately show `Booking Status: WL 3` and `Current Status: RAC 1`. `POST /api/bookings/{reference}/cancel` is authenticated and owner-scoped. It locks the date/class/quota inventory row, cancels the booking, promotes the first eligible RAC booking to confirmed capacity, moves eligible waiting-list bookings into RAC, deterministically reindexes queues by booking time then ID, and publishes an availability/status event after commit.

## Payments

`PaymentProvider` is the payment-gateway boundary. The active `MockPaymentProvider` is clearly a development simulator and supports `createPayment`, server-side `verifyPayment`, and `refund`; a future authorised provider can replace it without changing `BookingService`. Payment states are `CREATED`, `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`, and `PARTIALLY_REFUNDED`.

The checkout offers **UPI**, **Card**, and **Net Banking** as simulated methods. It never collects or stores card numbers, CVV, UPI IDs, or bank credentials. `POST /api/payments` creates a provider payment reference from the authoritative fare; `POST /api/bookings` verifies that reference server-side before allocating seats or confirming a booking.

## Checkout review

`POST /api/checkout/review` validates the caller's active hold and passenger count, then returns the authoritative journey details plus a backend `FareResponse`: `baseFare`, `reservationCharge`, `tax`, `serviceCharge`, and `total`. The checkout page renders these values directly and labels the payment flow as a development simulation. If the hold expires, payment is disabled and the frontend fetches the latest availability before requiring a new search/hold.

## Ticket PDF

`GET /api/bookings/{reference}/ticket.pdf` is authenticated and owner-scoped: a user cannot download a ticket for a guessed reference belonging to another account. It generates a RailYatra-branded PDF from the saved booking, including passenger current statuses, fare breakdown, payment reference, booking timestamp, and an internal verification QR code. Local-provider PDFs prominently state that they are RailYatra development/internal reservations and not official Indian Railways/IRCTC tickets.

## Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Backend port |
| `RAILYATRA_DB_URL` | In-memory H2 | JDBC URL |
| `RAILYATRA_DB_USERNAME` | `sa` | Database username |
| `RAILYATRA_DB_PASSWORD` | empty | Database password |
| `RAILYATRA_JWT_SECRET` | required | JWT signing secret (32+ characters) |
| `RAILYATRA_BOOKING_HORIZON_DAYS` | `120` | Lazy-inventory booking horizon |
| `RAILYATRA_HOLD_DURATION_SECONDS` | `600` | Checkout hold duration |
| `RAILYATRA_HOLD_EXPIRY_CHECK_MS` | `15000` | Automatic expiry scan interval |
| `RAILYATRA_PNR_LOOKUP_MAX_PER_WINDOW` | `10` | PNR lookup attempts allowed per client window |
| `RAILYATRA_PNR_LOOKUP_WINDOW_SECONDS` | `60` | PNR lookup rate-limit window |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Frontend API base URL |

Run backend tests locally:

```powershell
cd .\railyatra\backend
mvn test
```

Run the concurrent-booking integration test explicitly:

```powershell
mvn -Dtest=BookingConcurrencyIT test
```
# My Bookings milestone

`GET /api/bookings/mine?tab=UPCOMING&page=0&size=10` returns `{content,page,size,totalElements,totalPages}`.
Tabs are `UPCOMING`, `COMPLETED`, `CANCELLED`; size is 1–50, page is zero-based. Queries always use the JWT principal's user ID, including for admins. There is no client-supplied user ID or cross-user admin listing in this endpoint. Results use database pagination and stable newest-first ordering; no passenger names are exposed in the listing.

Upcoming means a non-cancelled booking dated today or later in Asia/Kolkata; Completed means a non-cancelled booking dated before today. These are journey-date categories, not live train-arrival tracking. Cancelled overrides either date category. Failed/pending reservations retain their actual status; cancellation is permitted only for upcoming CNF/RAC/WL reservations and is validated again on the server. The confirmation dialog cancels the whole reservation. This milestone does not add refund processing.

Created: `MyBookingsService.java`, `MyBookingsServiceTest.java`, `V15__my_bookings_indexes.sql`, `my-bookings.css`. Changed: `BookingRepository.java`, `BookingController.java`, `ReservationStatusService.java`, `AccountPage.tsx`, `main.tsx`, this README. V15 adds user/date/status and user/created/id indexes; no existing booking data is recreated. The existing detail, ticket PDF and cancellation APIs are reused.

Run these commands from your extracted `railyatra` directory in Windows PowerShell, using two terminals (with the existing environment configuration):

```powershell
Set-Location .\backend
mvn -Dtest=MyBookingsServiceTest test
mvn spring-boot:run
```

```powershell
Set-Location .\frontend
npm install
npm run build
npm run dev
```

Manual verification: open http://localhost:5173/my-bookings, sign in, check all three tabs and next/previous with more than 10 reservations. VIEW opens saved details; DOWNLOAD TICKET uses bearer-authenticated PDF access. Cancel an eligible reservation after reviewing the cancellation quote; verify a fully cancelled booking moves to Cancelled and no cancel action remains. Use a second account to confirm the first account's bookings do not appear. Signed-out API calls must be rejected; invalid tab/page/size must return 400. Frontend build was verified here; backend tests need to be executed on a Java 21 + Maven environment.

## Passenger cancellation milestone

My Bookings → CANCEL opens `/bookings/:reference/cancel`. Select all active passengers for full cancellation or a subset for individual cancellation. The review shows passenger names, cancellation charges and the estimated refund, all calculated by the server. Changing selection invalidates the displayed quote. Quotes expire after five minutes; final confirmation rechecks ownership, eligibility, selected passenger state and current charges under the inventory lock. The same confirmed quote can be retried safely without releasing seats or requesting a refund twice.

Authenticated APIs:

| Method | Path | Request |
| --- | --- | --- |
| POST | `/api/bookings/{reference}/cancellation-quote` | `{ "passengerIds": [123, 124] }` |
| POST | `/api/bookings/{reference}/cancel` | `{ "quoteId": "server-issued-UUID" }` |
| GET | `/api/bookings/{reference}/cancellations` | Saved confirmed cancellations and refund requests |

The previous body-less full-cancellation API is replaced by reviewed-quote confirmation. Full cancellation uses all active passenger IDs from the owner-scoped booking details API. Passenger IDs from another booking, duplicates, already cancelled passengers, expired quotes and changed charges are rejected. Non-owner references return 404. Today/future local confirmed, RAC and WL bookings are eligible; provider reservations require their authorized cancellation adapter.

`V16__passenger_cancellation.sql` adds per-passenger cancellation state and durable cancellation/refund requests, backfills old fully cancelled bookings, and adds lookup/refund-queue indexes. Cancellation, seat deletion, confirmed/RAC/WL inventory release, booking update, ordered promotion and the refund request commit in one transaction. Availability and booking-status events are sent after commit. Promotion uses only active passenger counts and retains original passenger indexes when assigning seats. Cancelled passengers remain in booking history/PDF/PNR with CANCELLED status; My Bookings shows the remaining active passenger count. Original fare and booking status are preserved for audit.

Paid fare is split deterministically in paise so repeated partial cancellations cannot exceed the original payment. The RefundService milestone below extends the original flat development charges with configurable time/class/status rules and completes simulated refunds asynchronously. All values are RailYatra synthetic policy values, not official railway cancellation rules. The payment provider call remains outside the cancellation transaction.

Created: CancellationController, CancellationService, CancellationQuote, CancellationQuoteRepository, CancellationPolicy, V16 migration, CancellationPolicyTest, CancellationIntegrationTest, CancellationPage.tsx, cancellationRules.js/.d.ts/.test.mjs. Changed: Booking/BookingPassenger and DTO/repository, BookingController/BookingService, ReservationStatusService, SeatAllocationService, MyBookingsService/test, PnrService, TicketPdfService, application.yml, AccountPage, main.tsx, my-bookings.css, frontend package.json, README.

From the extracted `railyatra` directory in separate PowerShell terminals:

```powershell
Set-Location .\backend
mvn "-Dtest=CancellationPolicyTest,CancellationIntegrationTest,MyBookingsServiceTest" test
mvn spring-boot:run
```

```powershell
Set-Location .\frontend
npm install
npm test
npm run build
npm run dev
```

Manual checks: book two passengers; cancel only one after reviewing the quote. Verify the other retains their seat, the cancelled passenger is marked in details/PDF/PNR, and the active count decreases. Cancel the remainder and verify the booking becomes fully CANCELLED. Check queue promotion with RAC/WL bookings on the same inventory. Retry the same quote confirmation and verify only one saved refund request. Refresh the cancellation page to see persisted requests. Reject foreign-account downloads/cancellations and expired quotes. Integration tests also cover rollback of all cancellation changes and quote invalidation after promotion. Frontend tests and production build passed here; backend compilation and integration tests remain unverified because this environment has Java 17 and no Maven.

## RefundService milestone

RefundService calculates `{paid,deduction,refund}` per selected passenger from the original paid share, boarding journey timestamp, cancellation timestamp, class, current passenger status, and configured RefundRules. The cancellation quote and final confirmation use this same calculation; changed charges require a fresh review. Journey times use Asia/Kolkata and the saved boarding stop's departure time. After departure the development policy deducts the whole paid share. Charges are rounded to paise and capped at the paid amount.

Default **development-only** rules:

| Status / time before departure | Deduction |
| --- | --- |
| CNF, at least 48 hours | Class minimum, default ₹60 |
| CNF, at least 12 but less than 48 hours | Greater of class minimum and 25% of paid fare |
| CNF, less than 12 hours | Greater of class minimum and 50% of paid fare |
| RAC, before departure | ₹20 |
| WL, before departure | ₹0 |
| Any refundable status at/after departure | Full paid share |

Configure environment variables `RAILYATRA_CANCELLATION_CONFIRMED_CHARGE`, `RAILYATRA_CANCELLATION_RAC_CHARGE`, `RAILYATRA_REFUND_WL_CHARGE`, `RAILYATRA_REFUND_ADVANCE_HOURS`, `RAILYATRA_REFUND_NEAR_HOURS`, `RAILYATRA_REFUND_INTERMEDIATE_RATE`, `RAILYATRA_REFUND_NEAR_RATE`. Rates are fractions from 0 to 1. Class-specific minimum deductions can be supplied through configuration, e.g. `railyatra.refunds.rules.class-charges.3A=90.00`. Validation rejects negative charges, invalid rates and overlapping time windows. No rule is presented as official Indian Railways policy.

Confirmed cancellations create `REFUND_PENDING` records. A worker claims a request as `REFUND_PROCESSING`, invokes `PaymentProvider.refund(RefundCommand)` outside reservation/database transactions, then records `REFUNDED` or `REFUND_FAILED` in a short transaction. Payment rows serialize refunds for each booking; amounts cannot exceed the saved verified payment. The cancellation quote UUID is the stable provider idempotency key. Expired processing leases can be recovered after a crash; stale workers cannot finalize a newer worker's claim. Provider adapters **must** honor the stable key across retries and timeouts. The development mock stores simulated gateway receipts in the database, so settlement replay works after backend restarts. No real funds move.

`GET /api/bookings/{reference}/refunds` is owner-scoped and returns refund reference, paid, deduction, refund, status, attempt count and a safe failure message. Existing cancellation history also shows settlement state. The frontend checks persisted settlement status every five seconds while the cancellation page is open. `POST /api/admin/refunds/{refundReference}/retry` queues a failed request using the same reference and is protected by ROLE_ADMIN on the server; ordinary users cannot trigger it. Failed requests are not blindly retried.

Worker configuration: `RAILYATRA_REFUND_WORKER_ENABLED` (default true), `RAILYATRA_REFUND_POLL_MS` (5000), `RAILYATRA_REFUND_LEASE_SECONDS` (180). It scans bounded batches of 25; a lease should exceed the provider's configured network timeout. Tests disable the background worker and drive it explicitly. Set `RAILYATRA_PAYMENT_PROVIDER=MOCK` for development. A future adapter implements PaymentProvider (including its type and idempotent refund command) and can be activated by configuration without changing RefundService or frontend code; mismatched stored payment provider types fail safely.

V17 adds processing leases, attempt/failure fields, refund-status constraints, and persistent mock refund receipts; existing PENDING rows migrate to REFUND_PENDING. Existing paid fare and cancellation quote amounts remain unchanged. Zero-refund cancellations retain NOT_APPLICABLE rather than scheduling a gateway call.

Created: RefundService, RefundRules, RefundStatus, RefundWorker, RefundController, MockRefundSettlementService, V17 migration, RefundServiceTest. Changed: PaymentProvider/MockPaymentProvider, PaymentTransaction/repository, CancellationQuote/repository, CancellationService, application.yml, CancellationIntegrationTest, MockPaymentProviderTest, test application.yml, CancellationPage.tsx, README. Tests cover time-window boundaries, class/status deductions, bounds, invalid rules, repeated and concurrent partial settlement, persisted mock receipt replay, and failed refund retry.

From `railyatra/backend` in Windows PowerShell:

```powershell
mvn "-Dtest=RefundServiceTest,CancellationPolicyTest,CancellationIntegrationTest,MockPaymentProviderTest,MyBookingsServiceTest" test
mvn spring-boot:run
```

From `railyatra/frontend`:

```powershell
npm test
npm run build
npm run dev
```

Manual verification: book and cancel a selected passenger after reviewing the refund quote. Observe REFUND_PENDING/REFUND_PROCESSING progress to REFUNDED within the polling interval. Refresh and confirm status persists, including after restarting the backend. Repeat confirmation and verify no second receipt. Cancel the remaining passenger and verify cumulative payment.refunded_amount stays below paid fare. Sign in with another account and verify refund history is inaccessible. To test failure/retry, disable the worker or use a failing development adapter, inspect REFUND_FAILED, then retry as admin using the same refund reference. Frontend tests and production build passed here; backend compilation/tests could not run because Java 21 and Maven are unavailable in this environment.

## Real-time PNR and booking status milestone

Authenticated booking owners now receive private SSE invalidations for RAC → CNF, WL → RAC, queue position changes, full/partial cancellation and every refund transition (pending, processing, completed, failed, retried). PNR page, saved booking details, My Bookings and the cancellation refund view re-fetch saved database state after events. The PNR page automatically looks up a reference supplied in its URL and displays connection state and private refund status for the owner.

| API | Access / purpose |
| --- | --- |
| `GET /api/pnr/{reference}/events` | Owner-only authenticated booking SSE stream |
| `GET /api/bookings/events` | Authenticated user's own booking/refund event stream |
| `GET /api/bookings/{reference}/pnr` | Owner-only authoritative PNR snapshot used on events/reconnect |
| `GET /api/bookings/{reference}/refunds` | Existing owner-only authoritative refund snapshot |

The existing public, masked PNR lookup remains rate-limited. Its old public event subscription has been secured: signed-out requests are rejected and another user's reference returns 404, including for admins. Authenticated snapshot refreshes use the private endpoint rather than exhausting the public lookup limiter. Bearer tokens are sent in request headers, never stream URLs. Clients stop on access rejection and show a session/ownership message.

V18 creates a transactional booking-status event outbox, owner/reference indexes and a delivery clock. Business operations insert invalidations in their transaction; a rollback removes them. A bounded relay assigns monotonically increasing delivery IDs only **after** a business transaction has committed, so an earlier-created event that commits later cannot be skipped by a subscriber cursor. Multiple backend instances use the same persisted relay and each instance can deliver events to its local connections. Payloads contain only the booking reference and refresh signal, not passenger details or mutable status values. The frontend always obtains current status from the database APIs.

SSE uses heartbeats every ten seconds and sixty-second connections so authorization is rechecked regularly. There are at most five concurrent streams per user per backend instance. Frontend reconnect uses bounded exponential backoff, detects a silent connection, aborts on logout/page changes, and performs a fresh snapshot fetch whenever a connected frame arrives. Duplicate or missed invalidations are safe because reconnect fetches the latest saved state; this does not claim exactly-once event delivery. Outbox rows are retained for audit in this milestone; deployment retention/archival should be configured for sustained traffic. Relay/dispatch interval is controlled by `RAILYATRA_STATUS_EVENT_POLL_MS` (default 1000).

Created: `V18__private_booking_events.sql`, `BookingEventsController.java`, `BookingRealtimeTest.java`, frontend `realtime/bookingStream.js`, its declarations/tests and `useBookingRealtime.ts`. Changed: BookingStatusEventPublisher, PnrController, PnrService, SecurityConfig, RefundService/test, CancellationService, application.yml, PnrStatusPage, BookingConfirmationPage, AccountPage, CancellationPage, frontend package.json, README.

Run backend checks in Windows PowerShell from `railyatra/backend`:

```powershell
mvn "-Dtest=BookingRealtimeTest,CancellationIntegrationTest,RefundServiceTest,MockPaymentProviderTest,MyBookingsServiceTest" test
mvn spring-boot:run
```

Run frontend from `railyatra/frontend`:

```powershell
npm test
npm run build
npm run dev
```

Manual verification: sign in as a booking owner in two tabs. Keep the PNR page open in one and cancel a relevant reservation in the other; observe passenger cancellation, RAC/WL promotion, new queue positions and refund settlement without refreshing. Keep My Bookings or confirmation open to check those updates too. Disconnect the browser network, make changes from another client, then reconnect: PNR should fetch the latest saved status. Restart the backend and verify reconnect works. Sign out or use another account and verify a private subscription is rejected. Backend tests cover owner checks, rollback invisibility and out-of-order commit delivery across separate publisher instances. Six frontend workflow/parser/reconnect tests and production build passed; backend compilation/tests remain unverified because Java 21 and Maven are unavailable in this environment.

## Admin dashboard milestone

`/admin` displays database aggregates for railway data, booking activity, reservation status, completed cancellation operations, revenue and active holds. Navigation is shown to ROLE_ADMIN users; the route also requires that role. Both backend endpoints independently use the existing server-side `/api/admin/**` ROLE_ADMIN authorization rule. The JWT filter reads the current active user's role from the database, so a client role value or stale token role claim cannot grant access. Registration continues to create ROLE_USER accounts only. No public admin bootstrap or default admin credentials are added.

| API | Purpose |
| --- | --- |
| `GET /api/admin/dashboard` | Fresh database aggregate DTO; ROLE_ADMIN; Cache-Control: no-store |
| `GET /api/admin/dashboard/events` | ROLE_ADMIN SSE invalidations and heartbeats; no private booking details |

| Metric | Definition |
| --- | --- |
| Total Trains / Stations | All records in their respective tables, including imported development data |
| Bookings Today | Bookings created during the current Asia/Kolkata calendar day, including pending/failed attempts; start inclusive, next midnight exclusive |
| Confirmed / RAC / Waiting List | Booking counts by current status across all journey dates; cancelled/failed/pending bookings excluded |
| Cancellations | Confirmed cancellation operations, including partial passenger cancellation; unconfirmed/expired quotes excluded |
| Revenue | Lifetime successful payment receipts minus recorded completed refunds; excludes failed/pending payments |
| Active Seat Holds | ACTIVE holds whose expires_at is strictly later than snapshot time; expired holds excluded even before the expiry worker runs |

All metrics are calculated in one SQL statement, with no entity-list fetching, React statistics, or cached availability assumptions. Booking timestamps use UTC storage and India business-day boundaries; JDBC parameters bind UTC wall time independently of the server/JVM timezone. Revenue also exposes gross receipts and completed refunds for reconciliation. Pending refunds reduce the net figure after settlement. In development these figures describe synthetic/internal railway data and simulated funds, clearly labelled in the UI.

SSE compares fresh database snapshots every five seconds **only while admins are connected**. Changed aggregates trigger a `dashboard-change` invalidation, and the frontend fetches a fresh snapshot. Unchanged snapshots send heartbeats. This observes committed bookings, promotion, cancellation, refund, hold creation/release/expiry and imports, including writes by another backend instance, without coupling every business service to dashboard counters. Connections last sixty seconds to regularly recheck authorization, have a limit of 32 per backend instance, and send no credentials in URLs. Reconnect triggers a new authoritative GET. Failed live connections retry with bounded backoff and the page has a 30-second polling fallback, a working manual Refresh button, skeletons, errors and a stale-data warning. Database failures close the stream and log the server exception instead of sending invented zeros.

`RAILYATRA_ADMIN_DASHBOARD_POLL_MS` controls the server interval (default 5000); keep it below the client's 45-second silence timeout. Live delivery is periodic rather than instant and aggregate equality suppresses unnecessary UI refreshes.

Created files:

- Backend `admin/AdminDashboardResponse.java`, `AdminDashboardRepository.java`, `AdminDashboardService.java`, `AdminDashboardEvents.java`, `AdminDashboardController.java`.
- `db/migration/V19__admin_dashboard_indexes.sql`.
- Backend tests `admin/AdminDashboardRepositoryTest.java`, `AdminDashboardSecurityTest.java`.
- Frontend `pages/AdminDashboardPage.tsx`, `pages/admin-dashboard.css`, `realtime/adminDashboardStream.js`, its TypeScript declarations and test.

Changed files: backend `application.yml`; frontend `main.tsx`, `styles.css`, `package.json`; this README. The previously inactive mobile menu now opens working navigation, including the admin link for authorized users. SecurityConfig's existing ROLE_ADMIN rule and ProtectedRoute are reused. V19 adds standalone booking-created/current-status, confirmed-cancellation and payment-status indexes. It adds no seeded statistics or user accounts. The existing hold status/expiry index is reused.

Backend tests cover empty-database zeros, real aggregate SQL against the Flyway schema, India day boundaries, current-status transitions, pending/failed payment exclusion, settled refund deductions, partial cancellations, expired holds, unauthenticated/ROLE_USER rejection on both APIs, allowed admin access and database role enforcement against an old admin claim. Frontend tests exercise change invalidation, reconnect refresh, heartbeat suppression, header credentials and non-admin access rejection.

Run from the extracted project root in Windows PowerShell. Use two terminals:

```powershell
# Terminal 1 — backend (Java 21 and Maven required)
Set-Location .\backend
$railyatraSecretBytes = New-Object byte[] 48
$railyatraRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$railyatraRandom.GetBytes($railyatraSecretBytes)
$env:RAILYATRA_JWT_SECRET = [Convert]::ToBase64String($railyatraSecretBytes)
$railyatraRandom.Dispose()
# Retain your existing database environment settings for a persistent database.
mvn "-Dtest=AdminDashboardRepositoryTest,AdminDashboardSecurityTest,BookingRealtimeTest,CancellationIntegrationTest" test
mvn spring-boot:run
```

```powershell
# Terminal 2 — frontend, starting from the same project root
Set-Location .\frontend
npm install
npm test
npm run build
npm run dev
```

Provision an admin by granting the role to an **already registered** account through a trusted database administrator connection; registration/frontend input cannot choose the role. For PostgreSQL, substitute the registered email in this SQL and run it in your database client:

```sql
UPDATE users SET role = 'ROLE_ADMIN'
WHERE lower(email) = lower('your-registered-email@example.com') AND active = true
RETURNING id, email, role;
```

Confirm it returns the intended account, then sign out/in so authenticated navigation reflects the change. Use a persistent database for retained accounts; the default in-memory H2 configuration loses accounts on backend restart. No password or connection secret belongs in this SQL or source code.

Manual verification:

1. As an authorized admin, open `http://localhost:5173/admin`. Compare counts with database queries and inspect the revenue reconciliation. With no bookings, financial/activity values should be zero; imported station/train counts should reflect the database.
2. Keep the dashboard open while another user creates a hold, books, cancels passengers and receives a refund. Counts should change within the configured poll interval without refresh. Confirm an expired hold disappears even before the expiry sweep completes.
3. Trigger RAC/WL promotions and check the current-status counts. Unconfirmed cancellation quotes and unsuccessful payments must not inflate cancellations/revenue.
4. Disconnect/reconnect or restart the backend. The page shows its connection state, then reloads authoritative statistics when connected. Refresh explicitly also fetches the API.
5. Sign in as ROLE_USER and visit `/admin` directly: the route redirects. Calling either admin endpoint with that user's bearer token must return 403. Anonymous calls must be rejected too. Remove an admin's database role and verify new GET/stream requests are rejected.

Validation in this environment: all eight frontend tests and the production build passed; the Vite dev server serves `/admin`. Backend compilation/tests and live database integration remain unverified because only Java 17 is installed and Maven is unavailable. Run the listed Java 21/Maven checks before considering this milestone fully verified; no later milestone is included.

## Admin train management milestone

Admin → Manage trains (`/admin/trains`) now provides a database-paginated list, number/name search, active filtering, train creation/editing, ordered route editing with station autocomplete, day/class selection and confirmed deactivation. Both navigation and routes require ROLE_ADMIN, and **all** `/api/admin/trains` endpoints inherit the server-side ROLE_ADMIN rule.

| API | Behavior |
| --- | --- |
| `GET /api/admin/trains?q=&active=&page=0&size=20` | Paginated DTOs; maximum page size 50 |
| `GET /api/admin/trains/options` | Supported type/class/day choices |
| `GET /api/admin/trains/{id}` | Saved train and ordered route for editing |
| `POST /api/admin/trains` | Validated atomic create; 201 and Location header |
| `PUT /api/admin/trains/{id}` | Validated transactional update; requires current `version` |
| `PATCH /api/admin/trains/{id}/deactivate` | Body `{"version":N}`; retains data, blocks new searches/holds |

Requests configure `trainNumber`, `name`, `type`, `source`, `destination`, `runningDays` (MONDAY…SUNDAY), `classes` (1A/2A/3A/3E/SL/CC/EC/2S), ordered `stops`, and `version` when editing. A stop contains `stationCode`, `stopSequence`, `arrival`, `departure`, `dayOffset` (0-based), `distanceKm` (cumulative). Endpoints derive from the first/last TrainStop and must match the declared source/destination; no endpoint-only search shortcut is introduced.

Server validation enforces a unique normalized train number (backed by the existing database unique constraint), nonblank name, supported type/classes, different valid active stations, at least one unique running day/class, two to one hundred unique ordered stops, increasing distance and valid forward-running times. The source starts at day/distance zero. Invalid body/enum/parameter values return safe 400 responses; missing trains return 404; duplicate/stale/unsafe configuration changes return 409.

Admin updates and new holds/lazy inventory creation share a database train-row lock. Version checks prevent one admin from silently overwriting another's edits, including changes only to child rows. Route, number, running-day and class changes are blocked when bookings exist or unexpired ACTIVE holds exist; names/types remain editable. Removed classes are deactivated without deleting referenced rows. Route edits deactivate old fare rules, and new classes need coach/seat/fare configuration. Existing booking/hold records are retained on train deactivation. Previously valid checkout holds may finish; new holds and availability requests reject inactive trains/classes. Requested dates are checked against saved schedules before new inventory/holds are provided.

Train type and running-day data are also exposed by public train-search DTOs. Admin-created catalog records use `LOCAL_ADMIN`; synthetic data retains `SYNTHETIC`. Neither is represented as official railway information.

V20 adds `trains.train_type`, `trains.version`, a type constraint, active/number and train/hold/expiry indexes, and transactional development seed markers. The foundation seed runs once and preserves later admin changes. The explicit demo profile imports the large dataset once, uses persistent isolated H2 storage, generates no date-inventory bulk rows and supplies server-configured demo roles. It requires passwords from environment variables and has no public bootstrap endpoint. A cumulative-distance bug in the synthetic generator was corrected so the development dataset passes import validation.

Created: `admin/AdminTrainRequest`, `AdminTrainResponse`, `AdminTrainService`, `AdminTrainController`; `railway/domain/TrainType`; V20 migration; `demo/DemoInitializer`; `application-demo.yml`; `AdminTrainServiceTest`; frontend `AdminTrainsPage.tsx` and `admin-trains.css`; `start-demo.ps1`, `scripts/verify-admin-trains.ps1`, `DEMO_GUIDE.md`.

Changed: Train/TrainClass/TrainSchedule entities and repositories; TrainStopRepository; TrainSearchProjection/Response; AvailabilityService; SeatHoldService; ApiExceptionHandler; DevelopmentRailwaySeed; RailwayImportCommand; SyntheticRailwayDataGenerator/test; BookingConcurrencyIT fixture; frontend main routing and admin dashboard link; `.gitignore`; README. Existing application APIs are retained.

Added JUnit coverage: create/edit/deactivate persistence, intermediate route direction, uniqueness, same endpoints, empty days, stop order/distance/time validation, stale versions, class deactivation, active-hold/booking protection, scheduled-day checks, regular-user/anonymous rejection and admin creation. The existing concurrency fixture now supplies a valid running schedule. The generator test compares the entire repeated dataset. The demo HTTP smoke test exercises create → edit → intermediate search → deactivate → search exclusion → ROLE_USER denial.

Run the demo from the extracted `railyatra` root in Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-demo.ps1
```

For targeted backend checks independently, from `backend`:

```powershell
mvn "-Dtest=AdminTrainServiceTest,AdminDashboardRepositoryTest,AdminDashboardSecurityTest,SyntheticRailwayDataGeneratorTest,BookingConcurrencyIT" test
```

From `frontend`: `npm test`, `npm run build`, `npm run dev`. Full manual walkthrough, account setup, runtime prerequisites and verification limitations are in **DEMO_GUIDE.md**. Eight frontend tests, production build and HTTP serving of `/admin/trains` passed. Standalone generator validation passed (5,100 trains, 650 stations, 51,089 stops); full Java 21/Maven integration and Windows demo execution remain unverified here. This delivery includes a runnable launcher, not a claim that the complete backend demo ran in this environment.

# Cloud deployment preparation

Added `backend/Dockerfile`, `backend/.dockerignore`, `backend/railway.json`,
`backend/src/main/resources/application-cloud.yml`, and `DEPLOYMENT.md`.
Updated `WebConfig.java` and `application.yml` so CORS uses the
`RAILYATRA_CORS_ALLOWED_ORIGINS` environment allowlist while retaining the local
default. No new APIs or database migrations. No secrets are committed.
Frontend tests and build pass; Java 21 Docker/Maven verification and live
integration checks remain pending source upload. See `DEPLOYMENT.md` for the
cloud secret configuration and required same-origin API proxy.


## Verified cloud demo — 10 October 2026

Demo URL: https://railyatra-web-production.up.railway.app

The React frontend runs in its own Nginx service; /api/* is proxied to the Spring Boot backend over Railway's encrypted private network, with streaming buffering disabled for SSE. Vite builds with an empty VITE_API_BASE_URL so authentication cookies and API calls use the frontend origin. React routes support direct navigation. The frontend readiness check calls /api/health through this proxy.

Backend: Java 21 Maven verify passed 51 unit/component tests and 3 integration tests (BookingConcurrencyIT and TrainSearchPerformanceIT); none skipped. This supersedes the earlier local Java 17 verification limitations above. PostgreSQL initialization applied all 20 Flyway migrations. The initial deterministic import took 10.09 seconds: 650 stations, 5,100 trains, 51,089 stops, 5,100 schedules, 10,200 classes, 10,200 coaches, 81,600 static seats and 10,200 fare rules. Date-specific booking inventory remains lazy. Subsequent starts reuse the seed completion marker.

Frontend: 8 tests and the TypeScript/Vite production build passed in the cloud Docker build. Browser interaction verification is unavailable in the disconnected development workspace; use the manual checks below.

Created deployment files: frontend/Dockerfile, frontend/.dockerignore and frontend/nginx/default.conf.template. Changed backend Docker image sources, the SeatRepository/SeatAllocationService entity query, CancellationService inventory refresh under write lock, backend test configuration, test fixtures and Maven Failsafe integration-test execution. No new API or database migration was needed for this deployment.

Railway frontend service configuration:
- Root directory: /frontend; Dockerfile: Dockerfile; target port: 8080.
- PORT=8080, BACKEND_HOST=${{railyatra-api.RAILWAY_PRIVATE_DOMAIN}} and BACKEND_PORT=8080. The backend binds SERVER_ADDRESS=:: for private dual-stack access.
- Health check: /api/health.
- Backend CORS allowlist includes the exact frontend HTTPS origin.
- Database credentials, JWT signing key and demo account passwords are environment variables, never source files.

Windows PowerShell live verification (no local installation required):

```powershell
$RailYatraDemo = "https://railyatra-web-production.up.railway.app"
Invoke-RestMethod "$RailYatraDemo/api/health"
Invoke-RestMethod "$RailYatraDemo/api/stations/search?q=RNC"
$JourneyDate = (Get-Date).AddDays(2).ToString("yyyy-MM-dd")
Invoke-RestMethod "$RailYatraDemo/api/trains/search?from=BKSC&to=CNB&date=$JourneyDate&class=ALL&quota=GENERAL&page=0&size=10&sort=departure"
Start-Process $RailYatraDemo
```

Manual walkthrough: register or log in; select valid origin/destination and a future journey date; search, view route, choose class, enter passengers and review the server fare; complete the explicitly simulated payment; view the saved confirmation, download PDF, open My Bookings and review cancellation/refund. Admin credentials are supplied separately through the private handoff/environment; the admin page requires ROLE_ADMIN. Test admin create/edit/deactivate with a dedicated development train so existing routes are preserved.

All schedules/fares from the development generator are SYNTHETIC. Mock payments/refunds move no real money. Local-provider references and PDF tickets are internal RailYatra development reservations and are not official Indian Railways/IRCTC tickets.

### Backend connection fix — 10 October 2026

The public HTTPS upstream was returning 502 because Nginx could not verify its certificate chain. API proxy traffic now uses the environment-isolated Railway private network at railyatra-api.railway.internal:8080; the browser continues to use the same public HTTPS frontend origin. Runtime DNS is read from the container's resolver configuration and re-resolved every 10 seconds so backend redeployments do not leave a stale IP address in Nginx.

Changed frontend/nginx/default.conf.template and frontend/Dockerfile; created frontend/nginx/15-railyatra-resolver.envsh. Backend configuration enables dual-stack binding; frontend environment uses the backend private-domain reference. No API contracts, database migrations, fare logic or booking behavior changed. The frontend Docker build runs all 8 existing tests and the TypeScript/Vite production build. Deployment readiness uses /api/health through the actual frontend proxy.

## Visual train route editor

Admin → Manage trains → Create train or Edit opens a responsive vertical route timeline. Each stop shows its station autocomplete, automatically numbered stop position, arrival, departure, calculated halt in minutes, cumulative distance and journey day (Day 1 corresponds to API dayOffset 0). Add stops at any gap, remove intermediate stops, or reorder them using Up/Down and the position selector. Source and destination remain at the ends; their station selections can be edited. Reordering preserves each stop's values and requires invalid distance/time progression to be corrected before saving.

Halt is derived from persisted arrival/departure, avoiding an independent conflicting duration. A stop's arrival and departure currently share one dayOffset; cross-midnight halts at the same station are rejected explicitly. Overnight travel between stations uses increasing journey days. Routes support 2–100 distinct stations, day numbers 1–15, strictly increasing cumulative integer distances (up to 30,000 km) and arrival after the previous departure. Source is Day 1/0 km without arrival; destination has no departure.

Persistence uses existing ROLE_ADMIN endpoints: POST /api/admin/trains, PUT /api/admin/trains/{id}, GET /api/admin/trains/{id}. Admin stop DTOs now additionally return dayNumber and haltMinutes. Sequence numbers remain contiguous from 1. Server-side route validation, transaction/row locking, optimistic revision checks and route locks for existing bookings or active holds remain authoritative. No new database migration or API endpoint is required.

Created:
- frontend/src/components/TrainRouteEditor.tsx and train-route-editor.css
- frontend/src/components/routeEditorRules.js, routeEditorRules.d.ts and routeEditorRules.test.mjs

Changed:
- frontend/src/pages/AdminTrainsPage.tsx and frontend/package.json
- backend/src/main/java/com/railyatra/admin/AdminTrainResponse.java
- backend/src/test/java/com/railyatra/admin/AdminTrainServiceTest.java
- README.md

Validation includes 7 route workflow frontend tests, plus API tests proving reorder persistence after clearing the JPA persistence context, derived day/halt serialization, stale revision rejection and invalid route rejection. Existing admin authorization and booking-protection tests continue to apply.

From the repository root in Windows PowerShell:
```powershell
Push-Location .\backend
mvn verify
Pop-Location
Push-Location .\frontend
npm ci
npm test
npm run build
Pop-Location
powershell -ExecutionPolicy Bypass -File .\start-demo.ps1
```

Manual verification: sign in as admin, create a dedicated development train with four stations, set valid running days/classes and route times/distances, then save. Reopen Edit; move the two intermediate stops, adjust their cumulative distances/times, save and reopen again. Confirm station order, stop numbers, halt and Day 2 times survive reload. Repeat with duplicate station, decreasing distance or arrival before the preceding departure and confirm save is rejected. Check the layout and reorder controls on a mobile-width screen. The local browser workspace is offline; browser visual QA requires a connected browser.

Route editor release verification: frontend Docker test/build completed successfully and its proxied health check returned HTTP 200. Backend Java 21 Maven verify passed 53 unit/component tests plus 3 integration tests (56 total, none skipped), including the two new admin API route tests. Both deployed services reached SUCCESS. Browser visual interaction checks remain unavailable because the local execution workspace is disconnected.

Regression fix included: backend/src/main/java/com/railyatra/payment/RefundService.java now refreshes a previously loaded cancellation quote under its pessimistic write lock after acquiring the payment lock. This fixes a stale-version race exposed by the existing concurrent-refund test; claim, finish and retry keep their existing transaction/lease/idempotency behavior.

## Admin seat inventory management

Open Admin → Manage seat inventory. Select train number, journey date, class and quota. Counts are read from persisted inventory, active holds and non-cancelled booking passengers. Inventory initializes lazily within the configured booking horizon. Expired ACTIVE holds remain reserved until the expiry worker releases them; waiting-list checkouts are shown separately because they reserve no confirmed/RAC capacity.

ROLE_ADMIN is enforced at the endpoint and the service checks the current database role. Adjust total confirmed capacity and RAC capacity with a mandatory reason and review before confirmation. Available counts cannot be edited directly. Reductions below existing reservations/holds, increases beyond configured active physical seats, past-date changes and stale revisions are rejected. Capacity increases run the existing deterministic RAC/WL promotion engine. Existing confirmed seat assignments remain intact.

Adjustments use a database row lock, optimistic revision and an Idempotency-Key. The capacity change, promotions and append-only application audit record commit together. Audit history includes actor, timestamp, reason and before/after counts. No audit update/delete API exists. Availability events publish after commit; SSE reconnect and periodic authoritative refetch keep the screen current. Database revision polling also propagates inventory changes between backend instances.

APIs (all admin-only):
- GET /api/admin/inventory?trainNumber=...&date=YYYY-MM-DD&class=3A&quota=GENERAL
- POST /api/admin/inventory/{id}/adjustments with Idempotency-Key header and JSON {totalCapacity, racCapacity, expectedVersion, reason}
- GET /api/admin/inventory/{id}/adjustments?page=0&size=20

Database: Flyway V21 adds inventory_adjustment_audit, its unique retry constraint/history index, and hold/booking lookup indexes. Existing reservations are not rewritten.

Created: backend AdminInventoryController, AdminInventoryService, AdminInventoryResponse, AdminInventoryAdjustment, AdminInventoryServiceTest and V21 migration; frontend AdminInventoryPage.tsx, admin-inventory.css and inventoryRules.js/.d.ts/.test.mjs.
Changed: SeatInventory, ReservationStatusService, AvailabilityEventPublisher, frontend main.tsx, AdminDashboardPage.tsx, package.json and README.md.

Added seven backend tests covering safe/unsafe adjustment, physical capacity, promotion, idempotency, stale revisions, concurrent admins, rollback/after-commit events and authorization. Added three frontend validation tests.

Run from the repository root in Windows PowerShell:
```powershell
Push-Location .\backend
mvn verify
Pop-Location
Push-Location .\frontend
npm ci
npm test
npm run build
Pop-Location
powershell -ExecutionPolicy Bypass -File .\start-demo.ps1
```

Manual verification: sign in as admin, inspect a running train/date/class, adjust capacity with a reason, and inspect audit history. Open the same inventory in a second tab and confirm counts refresh. Attempt to reduce below reserved capacity and verify rejection; submit two adjustments against one revision and verify only one succeeds. Confirm a normal user cannot access these APIs. Use a dedicated development train when testing queue promotion. Browser interaction checks require a connected workspace.

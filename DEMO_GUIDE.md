# RailYatra local demo

Requires Windows PowerShell, Java 21, Maven and Node.js/npm. The script checks the toolchain and refuses to reuse occupied ports 8080/5173.

From the extracted `railyatra` directory containing `backend`, `frontend` and `start-demo.ps1`:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-demo.ps1
```

The launcher runs the targeted backend tests, packages Spring Boot, installs/tests/builds the frontend and starts two server windows. On first launch the explicit `demo` profile imports 5,100 synthetic trains and configures coaches/fares for the RNC–NDLS development services. Import duration and record counts appear in the backend log. Startup seeding uses transactional completion markers; restarts preserve admin edits rather than recreating renamed services.

Demo data persists in `backend/.demo-data/railyatra.mv.db`, separate from normal environment-configured databases. The demo profile is explicitly local, binds the backend to loopback and uses MockPaymentProvider. Static seats are imported; date inventory is still created lazily.

The terminal prints newly generated passwords for:

- `admin@demo.railyatra.invalid` — ROLE_ADMIN
- `traveller@demo.railyatra.invalid` — ROLE_USER

There are no default passwords in source code. Each launcher run rotates these two demo passwords; the initializer is inactive outside the `demo` profile. Passwords are supplied through environment variables and saved only as BCrypt hashes. Do not enable this profile against a production database.

Once ready, an HTTP smoke script logs in, creates a train, edits its name/type, checks intermediate search, deactivates it, verifies search exclusion and verifies ROLE_USER denial. It prints PASS only after those checks succeed. Its inactive test train remains visible in the isolated demo database. The browser then opens `http://localhost:5173/login`.

## Walkthrough

1. Sign in with the printed admin credentials. Open **Admin → Manage trains** (`/admin/trains`). Search by number/name or filter active/inactive records; results are paginated from the database.
2. Create a train with a unique number, name/type, running days and classes. Select source/destination using station autocomplete. Add intermediate stops, timings, day offsets and increasing cumulative distance. Save and reopen it to check persistence.
3. Try the same train number, matching source/destination, no running day or decreasing distance. The server must reject invalid configuration. Edit a valid train and verify the saved name/type/classes/days.
4. Deactivate the train through the confirmation dialog. It remains in admin history but disappears from public searches and refuses new seat holds. Existing tickets and valid pre-existing checkout holds remain intact.
5. Open a second private browser session as the traveller. Search **RNC → NDLS**, tomorrow, **SL** or **3A**. The demo seeds small eight-seat coach templates and synthetic fares for this route. Try booking, mock payment, confirmation, ticket download, My Bookings and cancellation/refund views.
6. Keep the admin dashboard open while doing those actions. Its database-backed totals refresh after live invalidations. Keep PNR open while cancellation/promotion/refund activity occurs to inspect current-status updates.
7. Visit `/admin` or call `/api/admin/trains` as the traveller: access must be rejected server-side.

Newly created classes have no invented coach capacity or fares. They require separate coach/seat/fare setup before booking can open. This milestone adds train configuration; it does not add a coach/fare administration screen. Route edits invalidate old fare rules for review. Operational edits are protected once bookings exist or while checkout holds remain active; train name/type remain editable.

To stop: press Ctrl+C in both server windows. Restart using the same launcher to keep saved demo data. Do not delete `.demo-data` unless you intentionally want to discard the demo's users/bookings/configuration.

## Verification status of this delivery

- Frontend TypeScript/production build: passed.
- Eight existing frontend workflow/stream tests: passed.
- Frontend development server: started and served `/admin/trains` over HTTP.
- Standalone generator business logic: compiled/run using the available Java 17 compiler module after omitting only Spring's component annotation/import; determinism and import validation passed for 5,100 trains, 650 stations, 51,089 stops, 10,200 classes/coaches, 81,600 static seats and 10,200 fare rules. Generation, validation and a repeat run took approximately 3 seconds; this is **not database import time**.
- Full Spring Boot build, Flyway execution, JUnit integration tests and the Windows launcher: not run in the delivery environment, which lacks Java 21, Maven and Windows PowerShell. The launcher performs the backend checks on your machine before opening the demo. End-to-end booking behavior must be verified there; frontend builds alone do not establish it.

The current search results page still has earlier incomplete presentation work (advanced filter controls and per-class fare tiles). The new admin forms are connected to their APIs; those older results-page features should not be considered verified by this milestone.

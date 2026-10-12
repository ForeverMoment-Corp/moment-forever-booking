# Vendor App — Requirements, API Design & Rollout Guide


## Document status


| | |
|---|---|
| Owner | Booking service team |
| Consumers | Backend (Booking), Admin/ERP frontend, Vendor app frontend |
| Scope | Vendor task assignment, status lifecycle, photo evidence — **implemented inside the Booking microservice** |
| Out of scope | Vendor identity/profile CRUD and login (already live in the Platform service) |


## How this guide is split


This feature touches a lot of ground, so instead of one huge file, it's split into focused documents. Read them in this order:


| # | Document | What's in it |
|---|---|---|
| 1 | **README.md** (this file) | Requirement validation + which service owns what, and why |
| 2 | [02-data-model.md](./02-data-model.md) | New Booking-service entities and enums for vendor assignment |
| 3 | [03-api-reference.md](./03-api-reference.md) | Every endpoint: method, path, headers, request/response payloads |
| 4 | [04-gap-list.md](./04-gap-list.md) | APIs that were missing from the original ask, now added, with reasons |
| 5 | [05-error-handling.md](./05-error-handling.md) | Error codes, response shape, and a scenario-by-scenario table |
| 6 | [06-concurrency.md](./06-concurrency.md) | Race conditions and how each one is closed |
| 7 | [07-future-improvements.md](./07-future-improvements.md) | What's deliberately deferred past the first version |
| 8 | [08-challenges-ui-must-handle.md](./08-challenges-ui-must-handle.md) | Edge cases the frontend must handle, not just the backend |
| 9 | [09-end-to-end-flow.md](./09-end-to-end-flow.md) | The whole thing, start to finish, in plain English |
| — | [ui-handover.md](./ui-handover.md) | Standalone handover packet for the UI team |


---


## 1) Requirement validation


Restating every requirement gathered so far and confirming it against the **actual code already in this repo family** (Booking, Platform, Gateway). ✅ = confirmed buildable as stated. ⚠️ = confirmed but required an assumption (called out). 🚧 = real gap that needs a decision before/while building.


| # | Requirement (as given) | Status | Notes |
|---|---|---|---|
| 1 | Customers book experiences on the website; many users can book the same experience | ✅ | Already implemented — `Booking` entity, one row per booking, `experienceId`/`timeSlotId` reference shared inventory. No vendor app change needed here. |
| 2 | Vendor app shows only the bookings **assigned to the logged-in vendor** | ✅ | New `BookingVendorAssignment` table, every vendor query filtered by server-resolved `vendorId`, never a client-supplied one. |
| 3 | Vendor can update booking status: scheduled, completed, cancelled, etc. | ✅ | New `VendorTaskStatus` lifecycle, separate from the existing `BookingStatus` (see §2 for why). |
| 4 | One vendor cannot see another vendor's bookings | ✅ | Enforced at the query layer (`WHERE vendor_id = :callerVendorId`), not just hidden in the UI. |
| 5 | Vendor uploads a "starting work" photo with timestamp, and a "completed" photo with timestamp | ✅ | New `BookingVendorEvidence` table. Server timestamp is authoritative; client timestamp stored only as metadata — see [02-data-model.md](./02-data-model.md) and [08-challenges-ui-must-handle.md](./08-challenges-ui-must-handle.md) #11. |
| 6 | Admin (ERP) sees bookings, picks one, sees vendors filtered by location, assigns | ⚠️ | Buildable, but Platform's `Vendor` entity has **no location/service-area field today** — flagged in [04-gap-list.md](./04-gap-list.md) with an interim workaround. |
| 7 | Only **free/available** vendors should be assignable | ✅ | Availability = calendar/capacity overlap check owned by Booking service ([03-api-reference.md](./03-api-reference.md)), re-validated atomically at assignment time ([06-concurrency.md](./06-concurrency.md)). |
| 8 | Hyatt-style one-off custom requests (e.g. decoration not sold on the customer site) | ✅ (out of scope here) | Confirmed earlier: these go through a separate ops/partner intake, converted into a normal booking/order, **then** assigned like any other booking. No special API needed in this feature. |
| 9 | Vendor basic CRUD (register, profile, admin manage) | ✅ already exists | Lives in `moment-forever-platform-main` (`VendorController`, `VendorControllerAdmin`, `VendorService`, `vendors` table). This document does **not** duplicate it. |
| 10 | Authentication / JWT | ✅ already exists | Done once, at the gateway (`JwtAuthenticationFilter`). Login itself only happens in Platform (`/api/platform/auth/**`). Booking never issues tokens. |
| 11 | "For vendor backend we are using the booking service" | ✅ decided | All **new** endpoints in this document are added to the Booking microservice. See §2 for the full reasoning. |


---


## 2) Service ownership decision


This system already has three services behind one gateway: **Platform** (identity, auth, vendor master data, experiences), **Booking** (booking lifecycle), **Payment** (checkout + webhooks). The vendor-task feature touches two of them, so we need one explicit rule for "who owns what."


### Decision


| Capability | Owner | Why |
|---|---|---|
| Vendor registration, profile, admin CRUD, `VendorStatus` (ACTIVE/PENDING/INACTIVE) | **Platform** (unchanged) | Already built (`Vendor` entity + `VendorController`/`VendorControllerAdmin`). Identity/master-data naturally lives next to Auth. |
| Login, JWT issuance, role check (`VENDOR` role already seeded via `RoleDataSeeder`) | **Platform + Gateway** (unchanged) | Gateway validates the JWT once for every service; no service re-implements login. |
| "Which vendor is assigned to which booking", task status (ASSIGNED/SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED), audit trail, photo evidence | **Booking (new)** | A booking is the aggregate root for everything that happens around fulfilling it, and `Booking`/`BookingStatus`/`BookingRepository` already live here. Keeping assignment data next to the booking avoids a distributed transaction between two databases every time a booking's fulfillment state changes. |
| Picking *candidate* vendors (by category/status) for a booking | **Booking calls Platform** | Booking does not own vendor master data, so it asks Platform for active vendors in the right category, then applies its own availability/overlap logic (which only Booking can know, because only Booking has the assignment table). |


### Why `BookingStatus` is not reused for vendor task state


`BookingStatus` today is `PENDING, CONFIRMED, FAILED, CANCELLED` — it describes whether the **sale/payment** succeeded, not whether the **work got done**. A booking can be `CONFIRMED` for days before a vendor even starts. Overloading `BookingStatus` with `SCHEDULED`/`IN_PROGRESS`/`COMPLETED` would:
- conflate payment truth with fulfillment truth (exactly the anti-pattern this team already corrected once in `docs/booking-inbound-idempotency.md`);
- break every existing `status = CONFIRMED` check scattered through the payment/event-consumer code.


So fulfillment status lives on a **new, separate entity** (`BookingVendorAssignment`), and a booking only becomes assignable once `BookingStatus == CONFIRMED`.


### How Booking resolves "who is the calling vendor"


The gateway already forwards `X-User-Id` and `X-User-Roles` after validating the JWT (see `JwtAuthenticationFilter`). Two options, and we pick a primary + fallback:


1. **Recommended:** Platform's login adds a `vendorId` claim to the JWT when the authenticated user has the `VENDOR` role (nullable for everyone else). Gateway forwards it as a new header, `X-Vendor-Id`, the same way it already forwards `X-User-Id`. Booking trusts `X-Vendor-Id` directly — no extra network call per request.
2. **Fallback (until #1 ships):** Booking resolves `vendorId` by calling Platform once per session/token (e.g. an internal `GET /platform/internal/vendors/by-auth-user/{authUserId}` lookup) and caches it briefly in-memory. Slower, but needs no gateway/JWT change.


🚧 **Placeholder:** neither the `vendorId` JWT claim nor the internal lookup endpoint exists yet — this is a cross-team dependency (Platform + Gateway) that should be scheduled before vendor-app UI work starts. Tracked as a blocker in [07-future-improvements.md](./07-future-improvements.md).


⚠️ **Existing bug to not copy:** `JwtAuthenticationFilter` currently calls `.header("X-User-Id", username)` and then `.header("X-User-Id", String.valueOf(userId))` on the same header name — both values get attached, which is already flagged as a known gap in the gateway's own docs. Do **not** reuse `X-User-Id` as-is for anything vendor-identity-critical until that's fixed; `X-Vendor-Id` should be its own clean header from day one.




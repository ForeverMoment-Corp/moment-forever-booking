# 03 — API Reference


[← Back to index](./README.md)


All endpoints below are **new**, added to the Booking microservice. Existing endpoints (`/admin/bookings/{bookingId}`, `/api/bookings/{bookingId}`, etc.) are unchanged and not repeated here.


## Conventions used throughout this file


- **Base URL (external, via gateway):** `https://<gateway-host>/api/booking` — gateway route `/api/booking/**` strips `/api`, forwarding `/booking/**` to this service, whose own `context-path` is `/booking`. So a controller mapped at `/vendor/tasks` is reachable externally at `/api/booking/vendor/tasks`.
- **Dates:** `yyyy-MM-dd HH:mm:ss`, UTC — matches the service's existing global Jackson config.
- **Pagination:** standard Spring Data `Page<T>` JSON (`content`, `totalElements`, `totalPages`, `number`, `size`, `first`, `last`), same as the existing `GET /admin/bookings/user/{userId}`.
- **Errors:** always the existing `ApiErrorResponse` shape (`timestamp, status, error, code, message, path, fieldErrors`). Full code list in [05-error-handling.md](./05-error-handling.md) — only the relevant codes are named per endpoint below.
- **Admin headers (all admin endpoints):** `X-User-Id` (numeric), `X-User-Roles` (must contain `ADMIN`, checked in code — no `@PreAuthorize`, see README §2 on no Spring Security in this service).
- **Vendor headers (all vendor endpoints):** `X-Vendor-Id` (see README §2 for how this gets populated — 🚧 placeholder until the JWT claim / lookup ships). Every vendor query is filtered by this value server-side; it is never read from the request body or query string.
- **Money is never returned to vendor endpoints.** No field named `*amount*`, `*price*`, `*total*` appears in any vendor-facing response below — this is deliberate, not an oversight.


---


## A) Admin endpoints


Controller: `BookingVendorAdminController`, mapped at `/admin/bookings` (same base as the existing `BookingAdminController` — can live in the same class or a sibling one).


### A1. `GET /admin/bookings/unassigned`


**Purpose:** feed the ERP "needs a vendor" list. Only bookings that are `CONFIRMED` (payment done) and have no row in `booking_vendor_assignments` yet.


Query params: `page` (default 0), `size` (default 10).


Response `200`:
```json
{
  "content": [
    {
      "bookingId": "MFB-1735000000000-A3F2",
      "experienceName": "Rooftop Candlelight Dinner",
      "locationName": "Mumbai - Bandra",
      "bookingDate": "2026-03-10 19:00:00",
      "guestCount": 2,
      "confirmedAt": "2026-03-04 11:30:00"
    }
  ],
  "totalElements": 37,
  "totalPages": 4,
  "number": 0,
  "size": 10,
  "first": true,
  "last": false
}
```
No new error codes — empty `content` array if nothing is pending, not a 404.


---


### A2. `GET /admin/bookings/{bookingId}/eligible-vendors`


**Purpose:** power the "pick a vendor" screen. Returns only vendors that are (a) `ACTIVE` in Platform, (b) in the right category, and (c) **currently free** for this booking's time window (see [06-concurrency.md](./06-concurrency.md) for the overlap check).


Query params: `category` (optional manual override — see [04-gap-list.md](./04-gap-list.md) for why this is needed today).


Response `200`:
```json
{
  "bookingId": "MFB-1735000000000-A3F2",
  "requiredWindow": { "start": "2026-03-10 19:00:00", "end": "2026-03-10 21:00:00" },
  "eligibleVendors": [
    { "vendorId": 42, "businessName": "Bloom & Co Decorators", "category": "DECOR", "status": "ACTIVE" },
    { "vendorId": 57, "businessName": "Mumbai Event Crew", "category": "DECOR", "status": "ACTIVE" }
  ]
}
```
Errors: `404 BOOKING_NOT_FOUND` if the booking doesn't exist; `409 BOOKING_NOT_ASSIGNABLE` if the booking isn't `CONFIRMED` yet (e.g. still `PENDING`).


---


### A3. `POST /admin/bookings/{bookingId}/assignment`


**Purpose:** the "Assign" button. Creates the one-and-only assignment row for this booking.


Request body:
```json
{ "vendorId": 42 }
```
Response `201`:
```json
{
  "bookingId": "MFB-1735000000000-A3F2",
  "vendorId": 42,
  "status": "ASSIGNED",
  "assignedBy": 7,
  "assignedAt": "2026-03-04 11:35:00"
}
```
Errors:
| Status | Code | When |
|---|---|---|
| 404 | `BOOKING_NOT_FOUND` | bad `bookingId` |
| 409 | `BOOKING_NOT_ASSIGNABLE` | booking isn't `CONFIRMED` |
| 409 | `BOOKING_VENDOR_ALREADY_ASSIGNED` | a row already exists — use `PUT` (A4) to reassign instead |
| 409 | `BOOKING_VENDOR_NOT_ELIGIBLE` | `vendorId` isn't `ACTIVE`, wrong category, or got double-booked between A2 and this call (re-checked atomically, see §Concurrency) |


---


### A4. `PUT /admin/bookings/{bookingId}/assignment`


**Purpose:** reassign to a different vendor. Writes a `REASSIGNED` row to `booking_vendor_events` preserving the old `vendorId` before overwriting.


Request body:
```json
{ "vendorId": 57, "reason": "Original vendor reported unavailable" }
```
Response `200`: same shape as A3.
Errors: same table as A3, plus `409 BOOKING_VENDOR_INVALID_STATUS_TRANSITION` if the current assignment is already `COMPLETED` (a finished job can't be reassigned — cancel-and-recreate is a deliberate manual decision, not an automatic transition).


---


### A5. `DELETE /admin/bookings/{bookingId}/assignment`


**Purpose:** admin-initiated cancel of the assignment (not the booking itself). Requires a reason; sets status `CANCELLED`.


Request body:
```json
{ "reason": "Customer requested a different vendor brand" }
```
Response `200`:
```json
{ "bookingId": "MFB-1735000000000-A3F2", "status": "CANCELLED", "cancelledAt": "2026-03-04 12:00:00" }
```
Errors: `404 BOOKING_VENDOR_NOT_ASSIGNED` if there's nothing to cancel; `409 BOOKING_VENDOR_INVALID_STATUS_TRANSITION` if already `COMPLETED`.


---


### A6. `GET /admin/bookings/{bookingId}/assignment`


**Purpose:** current assignment state for the admin detail screen.


Response `200`:
```json
{
  "bookingId": "MFB-1735000000000-A3F2",
  "vendorId": 42,
  "businessName": "Bloom & Co Decorators",
  "status": "IN_PROGRESS",
  "assignedAt": "2026-03-04 11:35:00",
  "scheduledAt": "2026-03-10 18:30:00",
  "startedAt": "2026-03-10 18:35:00",
  "completedAt": null,
  "version": 3
}
```
Errors: `404 BOOKING_VENDOR_NOT_ASSIGNED` if no assignment exists yet (admins use A1/A2/A3 in that case).


---


### A7. `GET /admin/bookings/{bookingId}/assignment/events`


**Purpose:** full audit trail — "what happened and when" for support/ops.


Response `200`:
```json
{
  "bookingId": "MFB-1735000000000-A3F2",
  "events": [
    { "eventType": "ASSIGNED", "oldStatus": null, "newStatus": "ASSIGNED", "actorType": "ADMIN", "actorId": 7, "note": null, "createdAt": "2026-03-04 11:35:00" },
    { "eventType": "STATUS_CHANGED", "oldStatus": "ASSIGNED", "newStatus": "SCHEDULED", "actorType": "VENDOR", "actorId": 42, "note": null, "createdAt": "2026-03-05 09:00:00" },
    { "eventType": "STATUS_CHANGED", "oldStatus": "SCHEDULED", "newStatus": "IN_PROGRESS", "actorType": "VENDOR", "actorId": 42, "note": null, "createdAt": "2026-03-10 18:35:00" }
  ]
}
```
No new error codes beyond `404 BOOKING_VENDOR_NOT_ASSIGNED` (empty history case — distinguish "never assigned" vs "assigned but zero events," which should never actually happen since assignment itself logs an event).


---


### A8. `GET /admin/bookings/{bookingId}/evidence`


**Purpose:** let ops/admin view the vendor's uploaded start/completion photos without needing direct file-system or vendor-app access.


Response `200`:
```json
{
  "bookingId": "MFB-1735000000000-A3F2",
  "evidence": [
    { "evidenceType": "START", "fileUrl": "/files/vendor-evidence/MFB-1735000000000-A3F2/start.jpg", "capturedAtClient": "2026-03-10 18:34:50", "uploadedAtServer": "2026-03-10 18:35:02" },
    { "evidenceType": "COMPLETION", "fileUrl": "/files/vendor-evidence/MFB-1735000000000-A3F2/completion.jpg", "capturedAtClient": "2026-03-10 20:58:10", "uploadedAtServer": "2026-03-10 20:58:30" }
  ]
}
```
Errors: `404 BOOKING_VENDOR_NOT_ASSIGNED` if no assignment; empty `evidence` array (not an error) if assigned but nothing uploaded yet.


---


## B) Vendor endpoints


Controller: `VendorTaskController`, mapped at `/vendor/tasks`. Every method resolves the calling vendor from `X-Vendor-Id` and filters by it — a vendor can never pass another vendor's ID and see their data (see README §1, requirement 4).


### B1. `GET /vendor/tasks`


**Purpose:** "My Tasks" list screen.


Query params: `status` (optional, one of `VendorTaskStatus`), `page`, `size`.


Response `200`:
```json
{
  "content": [
    {
      "bookingId": "MFB-1735000000000-A3F2",
      "status": "ASSIGNED",
      "experienceName": "Rooftop Candlelight Dinner",
      "locationName": "Mumbai - Bandra",
      "bookingDate": "2026-03-10 19:00:00",
      "scheduledAt": null
    }
  ],
  "totalElements": 5,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```


### B2. `GET /vendor/tasks/summary`


**Purpose:** small counter widget on the vendor home screen ("3 assigned, 1 in progress today").


Response `200`:
```json
{ "assigned": 3, "scheduled": 2, "inProgress": 1, "completedToday": 4, "cancelledToday": 0 }
```


### B3. `GET /vendor/tasks/{bookingId}`


**Purpose:** task detail screen. **Amount-masked** — see the field list below.


Response `200`:
```json
{
  "bookingId": "MFB-1735000000000-A3F2",
  "status": "SCHEDULED",
  "experienceName": "Rooftop Candlelight Dinner",
  "experienceSlug": "rooftop-candlelight-dinner",
  "locationName": "Mumbai - Bandra",
  "pincode": "400050",
  "bookingDate": "2026-03-10 19:00:00",
  "timeSlotLabel": "Evening",
  "startTime": "19:00",
  "endTime": "21:00",
  "guestCount": 2,
  "customerFullName": "Asha Rao",
  "addons": [ { "addonName": "Rose petal setup" }, { "addonName": "Photographer (1 hr)" } ],
  "scheduledAt": "2026-03-10 18:30:00"
}
```
Fields deliberately **excluded**: `resolvedPricePerPerson`, `totalAmount`, `addonsTotal`, `grandTotal`, `pricingLevel`, addon `effectivePrice`, customer `email`. (Booking has no customer phone field today at all — flagged in [04-gap-list.md](./04-gap-list.md), not something this feature can add silently.)


Errors: `404 BOOKING_VENDOR_FORBIDDEN` if `bookingId` exists but is **not** assigned to the calling vendor — same "don't reveal it exists" 404 pattern as `BookingControllerPublic`, just with a vendor-specific error code for server-side log clarity (the HTTP body a vendor sees is the generic 404 `ApiErrorResponse`, not a tell-tale message).


### B4. `PUT /vendor/tasks/{bookingId}/schedule`


**Purpose:** vendor commits to a date/time. `ASSIGNED → SCHEDULED`.


Request body:
```json
{ "scheduledAt": "2026-03-10 18:30:00" }
```
Response `200`: same shape as B3.
Errors: `409 BOOKING_VENDOR_INVALID_STATUS_TRANSITION` if not currently `ASSIGNED`; `400 VALIDATION_FAILED` if `scheduledAt` is in the past or outside the booking's own date.


### B5. `POST /vendor/tasks/{bookingId}/start` (multipart/form-data)


**Purpose:** single atomic call — upload the "starting work" photo **and** transition `→ IN_PROGRESS` together, so the system can never end up `IN_PROGRESS` with no evidence.


Form fields: `photo` (file, required), `capturedAtClient` (optional timestamp string).


Response `200`:
```json
{ "bookingId": "MFB-1735000000000-A3F2", "status": "IN_PROGRESS", "startedAt": "2026-03-10 18:35:02", "evidenceId": 101 }
```
Errors: `409 BOOKING_VENDOR_INVALID_STATUS_TRANSITION` (must be `ASSIGNED` or `SCHEDULED`); `400 BOOKING_VENDOR_EVIDENCE_REQUIRED` (no file in the request); `400 BOOKING_VENDOR_EVIDENCE_INVALID` (wrong extension / over size limit — reuses the existing `app.file.allowed-extensions` / `max-file-size` config).


### B6. `POST /vendor/tasks/{bookingId}/complete` (multipart/form-data)


**Purpose:** same pattern as B5 for the finish photo. `IN_PROGRESS → COMPLETED` (terminal).


Form fields: identical to B5.
Response `200`:
```json
{ "bookingId": "MFB-1735000000000-A3F2", "status": "COMPLETED", "completedAt": "2026-03-10 20:58:30", "evidenceId": 102 }
```
Errors: same codes as B5, with the transition check requiring current status `IN_PROGRESS`.


### B7. `PUT /vendor/tasks/{bookingId}/cancel`


**Purpose:** vendor-initiated cancel (e.g. vendor genuinely can't make it). Requires a reason so admins aren't left guessing.


Request body:
```json
{ "reason": "Vehicle breakdown, cannot reach location in time" }
```
Response `200`:
```json
{ "bookingId": "MFB-1735000000000-A3F2", "status": "CANCELLED", "cancelledAt": "2026-03-10 10:00:00" }
```
Errors: `409 BOOKING_VENDOR_INVALID_STATUS_TRANSITION` if already `COMPLETED` or `CANCELLED`; `400 VALIDATION_FAILED` if `reason` is blank.


### B8. `GET /vendor/tasks/{bookingId}/evidence`


**Purpose:** let the vendor see their own uploaded photos (e.g. to confirm the upload actually went through after a flaky network — see [08-challenges-ui-must-handle.md](./08-challenges-ui-must-handle.md)).


Response `200`: same shape as A8, scoped to the calling vendor (`404 BOOKING_VENDOR_FORBIDDEN` if the booking isn't theirs).


---


[← Back to index](./README.md) · [Next: 04-gap-list.md →](./04-gap-list.md)




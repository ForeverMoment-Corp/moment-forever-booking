# 08 — Challenges the UI Must Handle


[← Back to index](./README.md)


These are not backend bugs to fix — they're real situations the backend design above expects the frontend (both the Admin/ERP screen and the Vendor app) to handle deliberately, not accidentally.


## 1. A 409 on "Assign" means the list you're looking at is stale
Because vendor availability is re-checked atomically at the moment of assignment ([06-concurrency.md](./06-concurrency.md)), the eligible-vendors list the admin fetched a minute ago can go stale if another admin acts first. On `409 BOOKING_VENDOR_ASSIGNMENT_CONFLICT` or `BOOKING_VENDOR_ALREADY_ASSIGNED`: show the backend's message, then **re-fetch the eligible-vendors list automatically** rather than letting the admin retry against the same stale data.


## 2. Photo upload on a flaky mobile network
This is the single riskiest UI interaction in the whole feature:
- **Validate client-side first** (file type against `jpg/jpeg/png/gif/webp`, size against the 10MB limit) before starting an upload — don't make the vendor wait for a round trip just to learn the file was always going to be rejected.
- **Disable the upload button immediately on tap** and show progress — the combination of a slow connection and an impatient second tap is exactly the double-submit case [06-concurrency.md](./06-concurrency.md) Race 5 is designed for, but the UI shouldn't rely on the backend to paper over a bad experience.
- **Handle "success but no confirmation received"** — if the upload times out from the UI's perspective but may have actually succeeded server-side, don't blindly re-upload-and-forget. Call `GET /vendor/tasks/{bookingId}` (or `.../evidence`) first to check current state before deciding whether to retry.
- **Compress large photos client-side** before upload where possible — vendors are on mobile data in the field, not office Wi-Fi.


## 3. Camera/photo-library permission prompts
Uploading start/completion evidence requires device camera or gallery permission. Handle denial gracefully: a vendor who declines the permission should see a clear explanation and a path to enable it in settings, not a silent failure or a generic network error.


## 4. Timezones
Every timestamp from the backend is UTC in `yyyy-MM-dd HH:mm:ss` format (no `Z` suffix, no offset — see [03-api-reference.md](./03-api-reference.md)). The UI must convert to the vendor's/admin's local timezone for display, and convert the other way when **sending** `scheduledAt`. Don't display raw backend strings as-is.


## 5. Buttons should reflect current status, not just hide on error
Show "Start" only when status is `ASSIGNED`/`SCHEDULED`, "Complete" only when `IN_PROGRESS`, etc. The backend enforces this regardless (see [06-concurrency.md](./06-concurrency.md)), but a button that's clickable and then always fails is a worse experience than one that's simply not shown. Treat the backend check as the real guarantee, and the UI check as a convenience, not the other way around.


## 6. A 404 on a vendor task might mean "not yours," not "doesn't exist"
By design ([05-error-handling.md](./05-error-handling.md)), `GET /vendor/tasks/{bookingId}` returns the same generic 404 whether the booking doesn't exist or simply isn't assigned to the calling vendor. The UI should show a generic "task not found" message and route back to the task list — it must **not** attempt to guess or expose which case it was (the backend deliberately doesn't tell it either).


## 7. Pagination, not "load everything"
Both `GET /vendor/tasks` and `GET /admin/bookings/unassigned` are paginated (`page`/`size`). Build the list screens (and any "assign to vendor" picker) expecting pages, not a full dump — don't assume a small vendor/booking count today means it'll stay small.


## 8. Idempotent retries can come back as 200, not just error or success-once
Per [06-concurrency.md](./06-concurrency.md) Race 3, a repeated "Start"/"Complete" tap that lands after the first one already succeeded may legitimately return `200` with the **current** state rather than an error. Treat that as a normal success response (refresh the screen with the returned state), not as a bug.


## 9. No push notifications yet — you have to poll
Until the Kafka/push-notification future improvement ships ([07-future-improvements.md](./07-future-improvements.md)), the vendor app only learns about a new assignment by calling `GET /vendor/tasks` / `GET /vendor/tasks/summary` again. Guidance: poll on a sane interval (e.g. 30–60s) while the app is in the foreground, back off or stop while backgrounded, and always refresh immediately when the app returns to foreground — don't poll every few seconds "to be safe," and don't rely on a stale cached list.


## 10. Don't optimistically update status in the UI before the server confirms
Given the race conditions this system explicitly guards against, a status change can legitimately be rejected (409) even when the UI thinks it should succeed. Update the on-screen status only after the server responds — showing "Completed" and then having to silently revert it to "In Progress" a second later is a worse experience than a short loading state.


## 11. Trust the server's upload timestamp, not the device's
`capturedAtClient` is stored but is explicitly **not authoritative** (a device clock can be wrong or manipulated) — `uploadedAtServer` is. Any UI (vendor or admin) displaying "when was this photo taken" should default to `uploadedAtServer`, and only show `capturedAtClient` as a secondary, clearly-labeled "device-reported" detail if at all.


## 12. Vendor-facing screens will never receive amount/price fields
`totalAmount`, `grandTotal`, `resolvedPricePerPerson`, addon prices, etc. are deliberately absent from every vendor endpoint response (see [03-api-reference.md](./03-api-reference.md)). Build vendor-app screens to simply not have a price field — don't assume it'll be `0` or `null` as a stand-in; it won't be present in the JSON at all.


## 13. Surface the backend's error message directly where reasonable
The backend was deliberately written to produce specific, human-readable `message` text for every error case (see [05-error-handling.md](./05-error-handling.md)) instead of generic text. Prefer showing that message (or a light rewording of it) over a generic "Something went wrong" — it was written with exactly this audience (admin or vendor, not a developer) in mind.


---


[← Back to index](./README.md) · [Next: 09-end-to-end-flow.md →](./09-end-to-end-flow.md)




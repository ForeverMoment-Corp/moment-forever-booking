# 06 — Concurrency


[← Back to index](./README.md)


Kept as its own file because every race condition below needs a concrete technical answer, not just a mention — these are the cases that look fine in a demo and then cause real incidents once two admins or a flaky mobile network are involved.


## Race 1 — Two admins assign a vendor to the same booking at the same instant


**Guarantee:** `booking_vendor_assignments.booking_id` has a **unique constraint** (see [02-data-model.md](./02-data-model.md)). Whichever `INSERT` commits first wins; the second hits a DB constraint violation, which the service catches and translates into the existing `409 BOOKING_VENDOR_ALREADY_ASSIGNED` — no silent overwrite is possible even under a true simultaneous write.


## Race 2 — Two bookings get assigned to the same vendor for overlapping times


This is the harder one — a unique constraint can't express "no overlapping time ranges for this vendor." Pattern:


1. Inside the **same transaction** as the insert, re-run the overlap check with a row lock on that vendor's existing non-terminal assignments:
   ```sql
   SELECT a.id
   FROM booking_vendor_assignments a
   JOIN bookings b ON b.booking_id = a.booking_id
   WHERE a.vendor_id = :vendorId
     AND a.status IN ('ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'SCHEDULED')
     AND b.booking_date < :newBookingWindowEnd
     AND :newBookingWindowStart < b.booking_date -- (+ duration, same overlap shape used earlier for the live-state check)
   FOR UPDATE
   ```
2. If this returns any row, abort with `409 BOOKING_VENDOR_ASSIGNMENT_CONFLICT` — a different code from `ALREADY_ASSIGNED` (see [05-error-handling.md](./05-error-handling.md)) because the *booking* isn't the duplicate here, the *vendor's time* is.
3. `FOR UPDATE` means a second concurrent request for the **same vendor** blocks until the first transaction commits or rolls back, then re-evaluates against the now-committed state — so there's no gap between "checked" and "inserted."


This is deliberately a short pessimistic lock, not a distributed lock/queue — admin assignment is a low-frequency, human-paced action (nowhere near high QPS), so row-level locking inside one transaction is sufficient and simple.


## Race 3 — Double-tap on a status-changing button (Start, Complete, Cancel, Schedule)


Same conditional-`UPDATE` technique already used in `BookingRepository` for `PENDING → CONFIRMED/FAILED/CANCELLED` — reused verbatim for vendor status:


```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
        UPDATE BookingVendorAssignment a
        SET a.status = com.forvmom.MomentForeverBooking.domain.enums.VendorTaskStatus.IN_PROGRESS,
            a.startedAt = :startedAt,
            a.updatedAt = :updatedAt
        WHERE a.bookingId = :bookingId
          AND a.vendorId = :vendorId
          AND a.status IN (
              com.forvmom.MomentForeverBooking.domain.enums.VendorTaskStatus.ASSIGNED,
              com.forvmom.MomentForeverBooking.domain.enums.VendorTaskStatus.SCHEDULED)
        """)
int transitionToInProgress(@Param("bookingId") String bookingId, @Param("vendorId") Long vendorId,
        @Param("startedAt") LocalDateTime startedAt, @Param("updatedAt") LocalDateTime updatedAt);
```
`rowsAffected == 0` means the WHERE clause didn't match — the service then re-fetches the current row to build a precise message ("current status is COMPLETED") before throwing `VendorInvalidStatusTransitionException`.


**UX nuance — don't punish an honest double-tap:** if a vendor's spotty connection causes the app to retry the same "Start" call and the *first* call actually already succeeded, a flat 409 reads like a bug to the vendor. Recommended behavior: before throwing, check whether the assignment is **already** in the target status (`IN_PROGRESS`) — if so, return the current **200** state instead of an error (idempotent retry), and only throw the 409 for a genuinely different/invalid transition (e.g. trying to "start" something already `CANCELLED`). Flagged here because it changes the service method's return contract slightly from the plain Booking pattern it's copied from; detailed UI-side handling in [08-challenges-ui-must-handle.md](./08-challenges-ui-must-handle.md).


## Race 4 — Admin reassigns while the vendor is mid-update


Admin calls `PUT .../assignment` (reassign) at the same moment the vendor calls `POST .../complete`. Both are guarded by the conditional-UPDATE/`@Version` combination:
- If the vendor's conditional UPDATE commits first, the admin's reassignment read sees `COMPLETED` and is rejected with `409 BOOKING_VENDOR_INVALID_STATUS_TRANSITION` ("can't reassign a completed task").
- If the admin's reassignment commits first (which changes `vendor_id` and resets status to `ASSIGNED`, bumping `@Version`), the vendor's conditional UPDATE WHERE-clause (`vendor_id = :callerVendorId AND status IN (...)`) simply matches zero rows — because the vendor_id on the row changed out from under them — and the vendor gets a clear "this task is no longer assigned to you, please refresh" `409`.


Either way, nobody's update is silently lost or silently overwritten — one of the two always gets a clear, honest error.


## Race 5 — Duplicate photo upload (double-tap on "Upload")


Evidence rows are **not** unique-constrained on `(booking_id, evidence_type)` on purpose — a vendor might legitimately re-upload a clearer photo. But an accidental instant double-submit shouldn't create two rows that both look "real." Recommended: compute `checksum_sha256` on receipt; if an evidence row with the same `booking_id`, `evidence_type`, and checksum already exists, return the **existing** `evidenceId` (200, not a new row) instead of inserting again. This makes the upload endpoint naturally idempotent against retries without needing a client-generated idempotency key.


## Open policy question — booking cancelled while a vendor task is `IN_PROGRESS`


Not purely a concurrency bug, but closely related (it's "two systems changing state on the same booking at once," just on a slower clock than the races above). Needs a product decision, not a code pattern:


| Option | Effect |
|---|---|
| **A — Cascade (recommended default)** | Booking cancellation automatically cancels any non-terminal `booking_vendor_assignments` row too, logging a `STATUS_CHANGED` event with `actorType = SYSTEM`. Vendor's next poll/screen shows "cancelled by customer." |
| B — Block | Booking cancellation is refused (409) while a vendor task is `IN_PROGRESS`, forcing a human to resolve it first. |
| C — Independent | The two stay out of sync until someone manually cancels the assignment too. **Not recommended** — this is exactly the kind of silent-drift bug that's hard to notice until a vendor shows up for a cancelled booking. |


Recommend **A**, but this should be confirmed with product/ops before building — tracked here rather than guessed silently.


---


[← Back to index](./README.md) · [Next: 07-future-improvements.md →](./07-future-improvements.md)




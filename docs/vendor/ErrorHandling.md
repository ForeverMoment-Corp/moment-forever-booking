# 05 — Error Handling


[← Back to index](./README.md)


No new error envelope is introduced. Every new exception plugs into the **existing** `ApiErrorResponse` / `BookingGlobalExceptionHandler` pattern already used by the rest of this service.


## Response shape (unchanged, for reference)


```json
{
  "timestamp": "2026-03-04 11:30:00",
  "status": 409,
  "error": "CONFLICT",
  "code": "BOOKING_VENDOR_INVALID_STATUS_TRANSITION",
  "message": "Cannot start task for booking 'MFB-1735000000000-A3F2' — current status is COMPLETED.",
  "path": "/booking/vendor/tasks/MFB-1735000000000-A3F2/start"
}
```
`fieldErrors` only appears for `400 VALIDATION_FAILED` responses, same as today.


## New error codes (add to `BookingErrorCode`)


| Code | HTTP | Meaning | Who sees it / what they should do |
|---|---|---|---|
| `BOOKING_NOT_ASSIGNABLE` | 409 | Booking exists but isn't `CONFIRMED` yet (still `PENDING`, or `FAILED`/`CANCELLED`). | **Admin:** "This booking isn't confirmed yet — it can't be assigned until payment completes." |
| `BOOKING_VENDOR_ALREADY_ASSIGNED` | 409 | Admin tried `POST .../assignment` but a row already exists. | **Admin:** "This booking already has a vendor — use reassign instead of assign." |
| `BOOKING_VENDOR_ASSIGNMENT_CONFLICT` | 409 | The atomic re-check at commit time found the vendor is no longer free (lost a race — see [06-concurrency.md](./06-concurrency.md)). | **Admin:** "This vendor was just booked for an overlapping job — pick another vendor." Distinct from `ALREADY_ASSIGNED`: this is a timing race, not a simple duplicate click. |
| `BOOKING_VENDOR_NOT_ELIGIBLE` | 409 | Chosen `vendorId` isn't `ACTIVE`, or wrong category. | **Admin:** "This vendor is not eligible for this booking (inactive or wrong category)." |
| `BOOKING_VENDOR_NOT_ASSIGNED` | 404 | No assignment row exists yet for reads/cancels that require one. | **Admin:** "No vendor is currently assigned to this booking." |
| `BOOKING_VENDOR_INVALID_STATUS_TRANSITION` | 409 | Requested transition isn't legal from the current status (e.g. completing a `CANCELLED` task). | **Vendor/Admin:** message names the current status explicitly, e.g. *"Cannot start task — current status is COMPLETED."* so nobody has to guess. |
| `BOOKING_VENDOR_FORBIDDEN` | 404 | Booking exists but is not assigned to the **calling** vendor. Returned as a plain 404 body — see note below. | **Vendor:** generic "booking not found" — deliberately not told it belongs to someone else. |
| `BOOKING_VENDOR_EVIDENCE_REQUIRED` | 400 | `start`/`complete` called without a `photo` file part. | **Vendor:** "A photo is required to start/complete this task." |
| `BOOKING_VENDOR_EVIDENCE_INVALID` | 400 | Wrong file extension or over the size limit. | **Vendor:** names the actual limit, e.g. *"File exceeds the 10MB limit"* or *"Allowed types: jpg, jpeg, png, gif, webp."* — reuses existing `app.file.*` config values in the message so it's never out of sync with the real limit. |


All of the above are added to the same `BookingErrorCode` enum (new block, commented `// ---- Vendor Assignment ----`), following the existing `BOOKING_<DOMAIN>_<REASON>` naming rule.


## New exception classes (same package, same pattern as `BookingStatusConflictException`)


Each is a small `RuntimeException` subclass that builds its own clear message in the constructor — the message is what gets shown in the API response, so it's written for a human, not a stack trace:


```java
@ResponseStatus(HttpStatus.CONFLICT)
public class VendorAssignmentConflictException extends RuntimeException {
    private final String bookingId;
    private final Long vendorId;


    public VendorAssignmentConflictException(String bookingId, Long vendorId) {
        super(String.format(
            "Vendor %d is no longer available for booking '%s' — it was assigned to an overlapping job. Please choose another vendor.",
            vendorId, bookingId));
        this.bookingId = bookingId;
        this.vendorId = vendorId;
    }
    // getters...
}
```
One class per row in the table above (`VendorNotAssignableException`, `VendorAlreadyAssignedException`, `VendorAssignmentConflictException`, `VendorNotEligibleException`, `VendorNotAssignedException`, `VendorInvalidStatusTransitionException`, `VendorForbiddenException`, `VendorEvidenceRequiredException`, `VendorEvidenceInvalidException`). Each gets one new `@ExceptionHandler` method in `BookingGlobalExceptionHandler`, following the exact `log.warn(...); return buildResponse(status, code, ex.getMessage(), path);` shape already used for every existing handler in that class — no new exception-handling mechanism, just more branches in the existing one.


## The "don't reveal it exists" rule applies here too


`BookingControllerPublic` already does this for customers (`BookingNotFoundException` thrown, not a 403, when a booking exists but belongs to someone else). The same rule applies to vendors: `GET /vendor/tasks/{bookingId}` for a booking that exists but isn't assigned to the caller returns **exactly the same 404 body** as a booking that doesn't exist at all. The distinct `BOOKING_VENDOR_FORBIDDEN` code exists only so **server logs** can tell the difference — the HTTP response itself must not.


## Writing clear messages — the rule for this feature


Every message above names **what**, **current state**, and (where relevant) **what to do next** — never a bare "Conflict" or "Invalid request." Before/after example used as the bar:


- ❌ "Invalid status transition."
- ✅ "Cannot start task for booking 'MFB-1735000000000-A3F2' — current status is COMPLETED."


This matters doubly here because there are **two different audiences** reading the same error shape (admin users in the ERP tool, vendors in a mobile app) — neither should need backend knowledge to understand what went wrong.


---


[← Back to index](./README.md) · [Next: 06-concurrency.md →](./06-concurrency.md)




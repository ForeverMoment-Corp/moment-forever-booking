# 07 — Future Improvements


[← Back to index](./README.md)


Split into two groups on purpose: **blockers** are things that should be resolved before or alongside the first build (not because the code can't be written without them, but because a workaround is already built in and someone should consciously decide whether that's acceptable). **Future enhancements** are genuine nice-to-haves, safe to defer indefinitely.


## Blockers (cross-team dependencies — track separately, don't let them slip)


| # | Item | Owner | Why it blocks a clean v1 |
|---|---|---|---|
| 1 | `vendorId` claim in the JWT, forwarded by gateway as `X-Vendor-Id` | Platform (issues JWT) + Gateway (forwards header) | Until this ships, every vendor endpoint relies on the fallback (`GET /platform/internal/vendors/by-auth-user/{authUserId}` lookup per request) — extra network hop, extra latency, and another service that must be up for the vendor app to work at all. |
| 2 | Fix the duplicate `X-User-Id` header bug in `JwtAuthenticationFilter` | Gateway | Not this feature's bug, but it's in the same header-forwarding code path this feature depends on for `X-Vendor-Id` — worth fixing in the same pass so nobody copies the broken pattern again. |
| 3 | Vendor `serviceAreaCity` / `serviceAreaPincodes` field | Platform | Without it, "assign vendors filtered by location" (an explicit original requirement) is manual/eyeballed by the admin. See [04-gap-list.md](./04-gap-list.md) Gap 1. |
| 4 | Category link between `Booking`/experience catalog and `Vendor.category` | Platform (experience catalog) + Booking | Without it, the eligible-vendors category filter needs a manual query param every time. See [04-gap-list.md](./04-gap-list.md) Gap 2. |
| 5 | Customer phone number captured somewhere reachable from a booking | Platform / Booking (wherever phone is ultimately sourced from) | Vendors will need to call the customer to coordinate arrival; no phone field exists on `Booking` today. See [04-gap-list.md](./04-gap-list.md) Gap 3. |


## Future enhancements (safe to defer)


### Backend / platform
- **Spring Security + `@PreAuthorize` in Booking.** Today's role checks are manual header reads (no Spring Security dependency in this service at all). Fine for a first version; worth revisiting once the service accumulates more role-gated endpoints than just booking + vendor-task.
- **Object storage for evidence photos** (S3-compatible, or Platform's existing GridFS-based `moment_forever_object_store`) instead of local disk. Local disk works for a single instance; it silently breaks (serves 404s for photos uploaded to a different pod) the moment this service runs more than one replica.
- **Event-driven vendor notifications.** Right now the vendor app must poll `GET /vendor/tasks` to notice a new assignment. A Kafka event (`VendorAssignedEvent`) or push notification would make "you have a new task" instant instead of poll-interval-delayed — this service already has Kafka producer/consumer infrastructure for booking events, so the plumbing pattern already exists.
- **Vendor capacity/skill fields in Platform** beyond category — e.g. max concurrent jobs per day, specific skills/certifications — to make eligible-vendor matching smarter than "active + right category + free."


### Product / workflow
- **Vendor accept/decline before commitment** (a pre-`ASSIGNED` `OFFERED` state) — see the open question in [04-gap-list.md](./04-gap-list.md).
- **Multi-vendor bookings** (e.g. decorator + photographer on one booking) — see [04-gap-list.md](./04-gap-list.md).
- **Bulk assignment** (assign N bookings to one vendor in one action).
- **SLA / overdue alerts** — flag bookings still `ASSIGNED` (not yet `SCHEDULED`) as the booking date gets close, so ops can chase it up before it becomes a same-day scramble.
- **Vendor rating/feedback loop** — let the customer rate the vendor after `COMPLETED`, feeding back into future eligible-vendor ranking (not just filtering).
- **Smarter vendor ranking, not just filtering** — once location/capacity data exists (blocker #3), rank eligible vendors by distance/rating/load instead of returning an unordered list for the admin to eyeball.
- **GPS-verified evidence** — today's evidence is a timestamped photo only; location-tagged photos would be a stronger proof-of-work signal if the business ever needs to dispute "vendor wasn't actually on site."


---


[← Back to index](./README.md) · [Next: 08-challenges-ui-must-handle.md →](./08-challenges-ui-must-handle.md)




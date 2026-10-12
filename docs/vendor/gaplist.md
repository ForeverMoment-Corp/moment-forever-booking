# 04 — Gap List: APIs Added Beyond the Original Ask


[← Back to index](./README.md)


The original requirement list (README §1) described the vendor app in terms of screens and actions ("vendor sees assigned bookings," "vendor marks status," "admin assigns a vendor"). Turning that into a real API surface exposed some gaps — either missing endpoints or missing data. This file lists every one of them explicitly, so nothing got added silently.


## Endpoints added that weren't explicitly requested


| Endpoint | Why it had to be added |
|---|---|
| `GET /admin/bookings/unassigned` | The ask only described assigning *a* booking, but admins need a worklist to find bookings that still need a vendor — otherwise they'd have to already know a `bookingId` before starting. |
| `GET /admin/bookings/{bookingId}/eligible-vendors` | Implied by "show list of vendors based on location," but it's a distinct read endpoint, not part of the assign call — the UI needs to render the picker *before* the admin commits to one vendor. |
| `GET /admin/bookings/{bookingId}/assignment` + `GET .../assignment/events` | The ask covered *writing* assignment state (assign, status updates) but not *reading it back*. Every write-only API eventually needs a matching read API once there's a support ticket asking "what happened to this booking." |
| `DELETE /admin/bookings/{bookingId}/assignment` | "Assign to a vendor" implies there must also be an "un-assign" — e.g. the vendor never responds, or the customer requests a different vendor. |
| `GET /admin/bookings/{bookingId}/evidence` | The ask said vendors upload photos; it didn't say how admins/ops see them. Without this, evidence photos would only be visible by someone with direct vendor-app or file-system access, which doesn't scale to a support team. |
| `GET /vendor/tasks/summary` | Not requested, but "make the dashboard look nicer" (an earlier ask in this same conversation) applies equally to the vendor app's home screen. Cheap to add, high UI value. |
| `GET /vendor/tasks/{bookingId}/evidence` | Lets the vendor confirm their own upload actually succeeded — important on a flaky mobile network (see [08-challenges-ui-must-handle.md](./08-challenges-ui-must-handle.md)). |
| `PUT /vendor/tasks/{bookingId}/schedule` | "Mark scheduled" was in the ask, but it needed its own endpoint (distinct from start/complete) because it's the only transition that takes a vendor-chosen future timestamp as input rather than just firing on an action. |


## Data gaps (not pure API gaps — these need a product/data decision)


### Gap 1 — Platform's `Vendor` has no location/service-area field
The requirement says "show vendors filtered by location," but `Vendor` (Platform) today has no city/pincode/service-area column at all — only `category`. **Interim workaround:** the eligible-vendors endpoint (A2) still returns all `ACTIVE` vendors in the matched category; the admin currently has to know coverage area from the vendor's `businessName`/`contactPhone` manually. **Recommended real fix (Platform-side change, outside this feature):** add `serviceAreaCity` or `serviceAreaPincodes` to `Vendor`, and filter by it in A2 once it exists. Tracked in [07-future-improvements.md](./07-future-improvements.md).


### Gap 2 — No category link between a `Booking` and `Vendor.category`
`Vendor.category` exists, but `Booking` doesn't store a comparable category/experience-type field (only `experienceId`/`experienceName`/`experienceSlug`) — so the system cannot *automatically* know "this booking needs a DECOR vendor." **Interim workaround:** `category` is an optional manual query parameter on A2; the admin picks it. **Recommended real fix:** add a `category` field to the experience catalog (Platform) and denormalize it onto `Booking` the same way `experienceName` already is, so A2 can filter automatically.


### Gap 3 — No customer phone number anywhere in `Booking`
If a vendor needs to call the customer to coordinate arrival, there is currently no phone field on `Booking` at all (only `userEmail`/`userFullName`). This is a **pre-existing** data gap, not introduced by this feature — flagged here because it will surface the first time someone builds the vendor task detail screen and asks "how do I call the customer?". Needs a product decision: capture phone at booking time, or look it up from Platform's user profile.


## Product questions surfaced while designing the APIs (not yet answered)


- **Does a vendor have the right to decline an assignment?** The current design is "push": admin assigns, and the only vendor-side way out is `PUT /vendor/tasks/{bookingId}/cancel` with a reason — which looks the same (from the data's perspective) whether the vendor never agreed at all or backed out after agreeing. If the business wants to measure "vendor acceptance rate" separately from "vendor cancellation rate," that needs a new pre-`ASSIGNED` state (e.g. `OFFERED` → vendor `ACCEPT`/`DECLINE`). Deliberately **not** added in this version to avoid growing the status enum speculatively — see [07-future-improvements.md](./07-future-improvements.md).
- **Can one booking ever need more than one vendor** (e.g. decorator + photographer on the same booking)? Today's model is one assignment row per booking. If multi-vendor bookings are real, `booking_vendor_assignments` would need a `role`/`serviceType` column and the unique constraint on `booking_id` would have to change to `(booking_id, role)`. Flagged, not built — no current requirement describes this.
- **Bulk assignment** ("assign these 10 bookings to this vendor in one click") was not requested and is deferred — see [07-future-improvements.md](./07-future-improvements.md).


---


[← Back to index](./README.md) · [Next: 05-error-handling.md →](./05-error-handling.md)




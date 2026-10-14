# Booking Cancellation Flow

How cancelling a booking works end to end: status update in the Booking
service, `BOOKING_CANCELLED` event publication, inventory release on the
platform side, and the customer email. Written as the reference for the cancel
flow — read this before changing anything in the cancellation path.

## 1. Rules

| From status | Cancellable? | Notes |
|---|---|---|
| `PENDING` | Yes | No money captured; nothing to refund. |
| `CONFIRMED` | Yes | Payment was captured → **ops refunds manually** (§6). |
| `FAILED` | No (409) | Its inventory was already released by the booking-failed flow; nothing to undo. |
| `CANCELLED` | Idempotent success | Repeat cancel returns 200 and stages **no** new event. |

Both entry points share one core: `PUT /admin/bookings/{id}/cancel` (admin,
optionally on the customer's behalf) and `PUT /user/bookings/{id}/cancel`
(owner only, `X-User-Id` must match). Both accept an optional `reason`, stored
as `cancel_reason` and carried into the event and the email.

## 2. End-to-end sequence

```
Admin / user            Booking service                     Kafka              Platform (core)
─────────────           ───────────────                     ─────              ───────────────
PUT .../cancel
  ?reason
       │
       ▼
cancelBooking(id, reason) @Transactional  ← returns staged outbox record
  1. CAS: UPDATE ... SET status=CANCELLED, cancelReason, cancelledAt
     WHERE bookingId=? AND status IN (PENDING, CONFIRMED)
     → 0 rows: CANCELLED→200/idempotent (stages nothing), missing→404, FAILED→409
  2. build BOOKING_CANCELLED event (booking snapshot + reason)
  3. outgoing_outbox_record (PENDING) ─┐ same TX commit
                                       │ TX returns record to controller
                         controller ───┘
  4. outgoingOutboxPublisher.trySinglePublish(record) — strictly AFTER commit
     (Kafka has no notion of our DB tx; publishing inside could notify the
     platform of a cancel that then rolls back)
       │  (failure → stays PENDING → Quartz publishPendingEvents retries 5x)
       ▼
                        topic_booking_cancelled
                                                                  │
                                                                  ▼
                                          BookingCancelledConsumer (group core-booking-cancelled-group)
                                            1. consumer_event_inbox.claim(producer, eventId)
                                               → 0 rows: DUPLICATE_EVENT, ack, stop
                                            2. releaseOnce(bookingId, slotMapperId, bookingDate, guestCount)
                                               → CAS RESERVED→RELEASED, decrement booked_count
                                               → already RELEASED: BUSINESS_NO_OP (never double-releases)
                                            3. inbox markProcessed ─┐ same TX
                                                                    ▼ ack
                                          BookingCancelledNotificationConsumer (booking service, self-consume)
                                            → cancellation email (reason + manual-refund line when ex-CONFIRMED)
```

## 3. Design decisions (what was reused, what was skipped)

**Reused from the booking-failed flow (deliberately mirrored):**

- **Atomic claim (CAS).** The `status IN (PENDING, CONFIRMED)` predicate is the
  concurrency guard: a payment confirmation racing a cancel — exactly one
  `UPDATE` wins. A late `payment-confirmed` for a cancelled booking matches 0
  rows and is ignored, same as for failed bookings.
- **Transactional outbox.** Status row + `BOOKING_CANCELLED` outbox row commit
  atomically; the controller publishes strictly after commit. Publishing inside
  the TX could notify the platform of a cancel that then rolls back (phantom
  release).
- **Post-commit publish** by the controller via `trySinglePublish` (never inside
  the TX — a rollback must never have published), then Quartz retry as the crash-window
  backstop, then DLQ + ops alert — identical split and guarantees to `BOOKING_FAILED`
  (`InboundEventProcessorService` stages inside, publishes outside).
- **Platform two-layer idempotency.** `consumer_event_inbox` dedups deliveries
  by `(producer, event_id)`; the reservation `RESERVED→RELEASED` CAS guards the
  business effect even across distinct event ids. Cancel reuses the same
  `BookingFailureEventProcessor` pipeline via the `InventoryReleaseEvent`
  interface — one pipeline, two event kinds, no duplicated inbox logic.
- **Notification pattern.** The cancelled email self-consumes
  `booking-cancelled` exactly like the confirmed email self-consumes
  `booking-confirmed` (ack-only-after-send, retry → DLT).

**Deliberately NOT used:**

- **Lease expiry.** There is no inventory TTL anywhere in the system (the only
  "leases" are HTTP-idempotency ownership on booking creation and outbox
  worker-claim tokens — neither applies to a terminal cancel). Nothing to
  expire; nothing added.
- **Fingerprint / request-id columns.** Booking-service identity is already
  `(producer, event_id)`; HTTP-initiated cancel needs no inbound dedup — the
  CAS makes double-cancel idempotent and `uq(outgoing booking,event)` backstops
  the outbox (unreachable in practice: a second win is impossible once the
  first commits `CANCELLED`).
- **Pessimistic locking.** No `SELECT ... FOR UPDATE` on these paths (same as
  the failed flow). Single-row CAS predicates already serialize the race;
  adding row locks would only add deadlock surface.
- **`@Version` in transitions.** The bulk CAS updates bypass the entity
  `@Version` (pre-existing behavior for all transitions); `clearAutomatically`
  evicts stale state before re-reads. Left as-is.

## 4. Event contract (`topic_booking_cancelled`)

- Key: `bookingId`. Producer type id:
  `com.forvmom.common.dto.events.BookingCancelledEvent` (mapped in
  `BookingKafkaConfig` both directions — produce tags it, the notification
  self-consumer resolves it).
- Fields: `bookingId, userId, userEmail, experienceId, timeSlotMapperId,
  guestCount, bookingDate (LocalDate), cancelReason (nullable), cancelledAt`
  plus base identity (`eventId` fresh UUID, `producer=booking-service`,
  `correlationId=bookingId`, no `causationId` — cancel is HTTP-initiated).
- `timeSlotMapperId + bookingDate + guestCount` are what the platform
  validates against the stored reservation before releasing.

## 5. Failure modes

| Failure | Outcome |
|---|---|
| Kafka down at cancel time | Immediate publish fails → record stays PENDING → Quartz (30s poll, 5 retries) → SENT. User still gets 200 (DB is source of truth). |
| Crash between commit and publish | Same as above — PENDING record picked up by Quartz. |
| Outbox retries exhausted | `BOOKING_CANCELLED` → DEAD + ops alert: *"platform inventory was NOT released — release reservation manually."* Same exposure class as dead `BOOKING_FAILED`. |
| Platform consumer fails (e.g. inventory row missing) | No ack → redelivery → `core-dlt` after retries. Reservation CAS keeps redeliveries safe. |
| Duplicate cancel (double click) | Second call sees `CANCELLED` → 200, no event. Duplicate deliveries deduped by inbox + CAS. |
| Payment succeeds mid-cancel | Atomic race: exactly one of confirm/cancel wins. Loser gets 409 (cancel) or ignored event (late confirm). |
| Stray payment AFTER cancel | Possible: the old payment link stays payable and `payment-retry` still issues links (see §7). Today this resolves via the manual refund process. |

## 6. Manual refund process (CONFIRMED cancels)

No auto-refund integration exists (no gateway refund clients, no
`REFUND_*` payment states). For a CONFIRMED cancel: the cancel email tells
the customer a manual refund is coming; ops refunds via the Stripe/Razorpay
dashboards and reconciles against the `BOOKING_CANCELLED` alert/event. The
cancel response itself does not distinguish PENDING vs CONFIRMED — check
`confirmedAt` in the booking detail.

## 7. Future scope / pending improvements

1. **Automated refunds.** Stripe `Refund.create` / Razorpay `payment.refund`
   clients, `REFUND_PENDING/REFUNDED` payment states, and a
   `payment-refund` flow so CONFIRMED cancels settle without ops touch.
2. **Block payments on cancelled bookings.** `payment-retry` (and late webhook
   success) currently still works against a `CANCELLED` booking — a deliberate
   deferral (§5). Guard it once refund automation lands.
3. **Cancellation window / policy.** No time-based rules today (e.g. "no cancel
   within 24h of slot", cancellation fees). Needs product policy first.
4. **Cancel actor audit.** `cancelReason` records *why* but not *who*
   (admin id vs owner). Add `cancelledBy` when audit needs it.
5. **DLQ replay tooling.** Dead `BOOKING_CANCELLED` needs manual reservation
   release today; a replay endpoint (requeue by outbox id) would match the
   retry tooling the failed flow is also missing.
6. **Email templates / i18n.** Cancel email is plain-text like the confirmed
   one; templating is a shared notification improvement, not cancel-specific.
7. **Metrics.** Cancelled counts already flow into the dashboard KPIs
   (`countByStatusInRange`); cancel-rate / reason-breakdown cards are open.

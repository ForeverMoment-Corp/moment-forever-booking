package com.forvmom.MomentForeverBooking.dto.response;


import java.time.LocalDateTime;


/**
 * One entry in a booking's state-history timeline, returned by
 * {@code GET /admin/bookings/{bookingId}/events}.
 *
 * <p>
 * PLACEHOLDER / KNOWN LIMITATION: the Booking service has no dedicated
 * event-log/audit table today, so this timeline is <b>derived</b> from the
 * single current {@code Booking} row (requestedAt/confirmedAt/updatedAt +
 * status), not replayed from persisted history. It is accurate for the
 * normal, single-transition flows this service supports
 * (PENDING -> CONFIRMED, PENDING -> FAILED, PENDING -> CANCELLED) but cannot
 * show intermediate/retried transitions. A future improvement is a real
 * {@code booking_status_history} table written on every transition.
 */
public class BookingTimelineEntry {


    private String status;
    private String label;
    private LocalDateTime occurredAt;
    private String reason; // only populated for FAILED entries


    public BookingTimelineEntry() {
    }


    public BookingTimelineEntry(String status, String label, LocalDateTime occurredAt, String reason) {
        this.status = status;
        this.label = label;
        this.occurredAt = occurredAt;
        this.reason = reason;
    }


    public String getStatus() {
        return status;
    }


    public void setStatus(String status) {
        this.status = status;
    }


    public String getLabel() {
        return label;
    }


    public void setLabel(String label) {
        this.label = label;
    }


    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }


    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }


    public String getReason() {
        return reason;
    }


    public void setReason(String reason) {
        this.reason = reason;
    }
}




package com.forvmom.MomentForeverBooking.controller;


import com.forvmom.MomentForeverBooking.domain.enums.BookingStatus;
import com.forvmom.MomentForeverBooking.domain.entity.OutgoingOutboxRecord;
import com.forvmom.MomentForeverBooking.dto.response.BookingMetricsResponse;
import com.forvmom.MomentForeverBooking.dto.response.BookingResponse;
import com.forvmom.MomentForeverBooking.dto.response.BookingTimelineEntry;
import com.forvmom.MomentForeverBooking.service.BookingService;
import com.forvmom.MomentForeverBooking.service.retries_cleanup.OutgoingOutboxPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;


@RestController
@RequestMapping("/admin/bookings")
@Tag(name = "Admin Booking Management", description = "Admin endpoints for fetching booking history and managing booking states")
public class BookingAdminController {

    // Allow-list of sortable fields for the list endpoint. Keeps `sort=` from
    // throwing an opaque 500 (Spring Data Sort-to-property resolution failure)
    // and from exposing internal entity fields that shouldn't be sortable.
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "createdAt", "updatedAt", "bookingDate", "confirmedAt", "grandTotal", "status");
    private static final String DEFAULT_SORT_FIELD = "createdAt";

    private final BookingService bookingService;
    private final OutgoingOutboxPublisher outgoingOutboxPublisher;

    public BookingAdminController(BookingService bookingService,
                                  OutgoingOutboxPublisher outgoingOutboxPublisher) {
        this.bookingService = bookingService;
        this.outgoingOutboxPublisher = outgoingOutboxPublisher;
    }
    @GetMapping
    @Operation(summary = "List/Filter Bookings", description = "Paginated, filterable, sortable list of bookings for the admin booking "
            + "dashboard grid. Every filter is optional and combinable; omit a filter to include every value for it. "
            + "from/to filter on the booking's createdAt timestamp (ISO-8601 date-time, e.g. 2026-01-01T00:00:00). "
            + "sort takes the form '<field>,<asc|desc>' (default 'createdAt,desc'); allowed fields: "
            + "createdAt, updatedAt, bookingDate, confirmedAt, grandTotal, status.")
    public ResponseEntity<Page<BookingResponse>> getAllBookings(
            @RequestParam(required = false) List<BookingStatus> status,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {


        validateRange(from, to);
        PageRequest pageRequest = PageRequest.of(page, size, parseSort(sort));
        Page<BookingResponse> bookings = bookingService.getAllBookings(status, locationId, customerId, from, to, pageRequest);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get Booking by ID", description = "Fetch full booking details by booking reference ID: amount breakdown, "
            + "service slot, and customer snapshot (userId/userEmail/userFullName/username). "
            + "PLACEHOLDER: payment mode/provider is not included — the Booking service does not persist it today "
            + "(see docs/booking-dashboard/README.md). Use GET /{bookingId}/events for a status timeline.")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable String bookingId) {
        BookingResponse booking = bookingService.getBookingByBookingId(bookingId);
        return ResponseEntity.ok(booking);
    }


    @GetMapping("/{bookingId}/events")
    @Operation(summary = "Get Booking Status Timeline", description = "Chronological state-history for one booking "
            + "(requested -> confirmed/failed/cancelled). "
            + "PLACEHOLDER / KNOWN LIMITATION: derived from the current booking row, not a persisted audit log "
            + "(see BookingTimelineEntry javadoc and docs/booking-dashboard/README.md).")
    public ResponseEntity<List<BookingTimelineEntry>> getBookingEvents(@PathVariable String bookingId) {
        return ResponseEntity.ok(bookingService.getBookingEvents(bookingId));
    }


    @GetMapping("/metrics")
    @Operation(summary = "Get Booking Dashboard KPIs", description = "Summary counts by status, conversion rate, and average "
            + "confirmed booking value, optionally scoped to a createdAt date range. Omit from/to for all-time metrics.")
    public ResponseEntity<BookingMetricsResponse> getBookingMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {


        validateRange(from, to);
        return ResponseEntity.ok(bookingService.getBookingMetrics(from, to));
    }


    @GetMapping("/user/{userId}")
    @Operation(summary = "Get Bookings for a User", description = "Paginated list of all bookings made by a specific user, newest first")
    public ResponseEntity<Page<BookingResponse>> getBookingsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {


        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<BookingResponse> bookings = bookingService.getBookingsByUser(userId, pageRequest);
        return ResponseEntity.ok(bookings);
    }


    @PutMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel Booking", description = "Cancel a PENDING or CONFIRMED booking by its reference ID "
            + "(e.g. on the customer's behalf). The cancellation is published as a BOOKING_CANCELLED event so the "
            + "platform releases the reserved inventory. Idempotent when already cancelled; 404 when absent; "
            + "409 when FAILED. CONFIRMED cancels require a manual ops refund. "
            + "An optional reason is recorded and emailed to the customer.")
    public ResponseEntity<?> cancelBooking(
            @PathVariable String bookingId,
            @RequestParam(required = false) String reason) {
        // Service stages the BOOKING_CANCELLED outbox record inside its TX and
        // returns it; publishing here runs strictly after commit (a rollback
        // must never have published). Failure to publish leaves the record
        // PENDING for the Quartz poller — same split as the failed flow.
        // trySinglePublish(null) is a no-op (already-cancelled idempotent path).
        OutgoingOutboxRecord staged = bookingService.cancelBooking(bookingId, reason);
        outgoingOutboxPublisher.trySinglePublish(staged);
        return ResponseEntity.ok(Map.of(
                "bookingId", bookingId,
                "status", "CANCELLED",
                "message", "Booking has been cancelled successfully."));
    }


    // ─── Query-param validation helpers ─────────────────────────────────

    /**
     * Sort parsing shared with the user controller via
     * {@link BookingQuerySupport}; behavior unchanged (default
     * "createdAt,desc", allow-listed fields, clear 400s).
     */
    private Sort parseSort(String sort) {
        return BookingQuerySupport.parseSort(sort, SORTABLE_FIELDS, DEFAULT_SORT_FIELD);
    }

    /** Shared inverted-range guard (see {@link BookingQuerySupport}). */
    private void validateRange(LocalDateTime from, LocalDateTime to) {
        BookingQuerySupport.validateRange(from, to);
    }
}


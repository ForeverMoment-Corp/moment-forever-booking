package com.forvmom.MomentForeverBooking.controller;

import com.forvmom.MomentForeverBooking.domain.enums.BookingStatus;
import com.forvmom.MomentForeverBooking.domain.entity.OutgoingOutboxRecord;
import com.forvmom.MomentForeverBooking.dto.response.BookingResponse;
import com.forvmom.MomentForeverBooking.service.BookingService;
import com.forvmom.MomentForeverBooking.service.retries_cleanup.OutgoingOutboxPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User self-service booking APIs mounted at {@code /user/bookings} —
 * the booking-service counterpart of the platform's {@code /user/booking}
 * split (admin vs user controllers, no public surface here).
 *
 * <p>Caller identity comes from the gateway's {@code X-User-Id} header (the
 * app-domain user id), exactly like the platform's user controllers. The
 * gateway strips client-supplied values, so this header is trusted — but only
 * behind the gateway. Every endpoint is hard-scoped to that id: there is no
 * way to request another user's bookings, and a foreign {@code bookingId}
 * returns the same 404 as a nonexistent one (no existence leak).
 */
@RestController
@RequestMapping("/user/bookings")
@Tag(name = "User Booking", description = "Self-service endpoints for a user to list, view and cancel their own bookings")
public class BookingUserController {

    // Narrower than the admin grid: only fields meaningful to a user timeline.
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "createdAt", "bookingDate", "grandTotal", "status");
    private static final String DEFAULT_SORT_FIELD = "createdAt";

    private final BookingService bookingService;
    private final OutgoingOutboxPublisher outgoingOutboxPublisher;

    public BookingUserController(BookingService bookingService,
                                 OutgoingOutboxPublisher outgoingOutboxPublisher) {
        this.bookingService = bookingService;
        this.outgoingOutboxPublisher = outgoingOutboxPublisher;
    }

    @GetMapping
    @Operation(summary = "List My Bookings", description = "Paginated list of the caller's own bookings, newest first. "
            + "Optionally filter by one or more statuses. "
            + "sort takes the form '<field>,<asc|desc>' (default 'createdAt,desc'); allowed fields: "
            + "createdAt, bookingDate, grandTotal, status.")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) List<BookingStatus> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {

        Sort parsedSort = BookingQuerySupport.parseSort(sort, SORTABLE_FIELDS, DEFAULT_SORT_FIELD);
        PageRequest pageRequest = PageRequest.of(page, size, parsedSort);
        return ResponseEntity.ok(bookingService.getUserBookings(userId, status, pageRequest));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get My Booking Details", description = "Full details of one of the caller's own bookings: "
            + "amount breakdown, service slot and add-ons. Returns 404 when the booking does not exist "
            + "or belongs to someone else.")
    public ResponseEntity<BookingResponse> getMyBookingById(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String bookingId) {

        return ResponseEntity.ok(bookingService.getUserBookingById(bookingId, userId));
    }

    @PutMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel My Booking", description = "Cancel one of the caller's own PENDING or CONFIRMED bookings. "
            + "The platform is notified so the reserved inventory is released. "
            + "Returns 404 when the booking does not exist or belongs to someone else, "
            + "409 when it is already failed. "
            + "Cancelling a CONFIRMED booking triggers a manual ops refund. "
            + "An optional reason is recorded and included in the confirmation email.")
    public ResponseEntity<?> cancelMyBooking(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String bookingId,
            @RequestParam(required = false) String reason) {

        // Same post-commit publish split as the admin endpoint: the service TX
        // stages the outbox record, publishing runs after it commits.
        OutgoingOutboxRecord staged = bookingService.cancelUserBooking(bookingId, userId, reason);
        outgoingOutboxPublisher.trySinglePublish(staged);
        return ResponseEntity.ok(Map.of(
                "bookingId", bookingId,
                "status", "CANCELLED",
                "message", "Booking has been cancelled successfully."));
    }
}

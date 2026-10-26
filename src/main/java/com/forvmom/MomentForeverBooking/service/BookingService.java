package com.forvmom.MomentForeverBooking.service;


import com.forvmom.MomentForeverBooking.domain.entity.OutgoingOutboxRecord;import com.forvmom.MomentForeverBooking.domain.enums.BookingStatus;
import com.forvmom.MomentForeverBooking.dto.response.BookingMetricsResponse;
import com.forvmom.MomentForeverBooking.dto.response.BookingResponse;
import com.forvmom.MomentForeverBooking.dto.response.BookingTimelineEntry;
import com.forvmom.MomentForeverBooking.events.InboundEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.time.LocalDateTime;
import java.util.List;


public interface BookingService {


    // Inbound event processing (already exists)
    OutgoingOutboxRecord processBookingRequest(InboundEvent event);


    // Query methods — return response DTOs, never entities: DTOs are mapped
    // inside the service transaction and carry no lazy proxies, so Jackson
    // can serialize them after the session closes.
    BookingResponse getBookingByBookingId(String bookingId);
    Page<BookingResponse> getBookingsByUser(Long userId, Pageable pageable);


    // Admin booking dashboard grid: every filter is optional (null/empty means
    // "no filter" for that field). from/to filter on createdAt.
    Page<BookingResponse> getAllBookings(List<BookingStatus> statuses, Long locationId, Long customerId,
                                  LocalDateTime from, LocalDateTime to, Pageable pageable);


    // User self-service (identity comes from the gateway X-User-Id header).
    // userId is mandatory here — unlike the admin grid it is always scoped.
    Page<BookingResponse> getUserBookings(Long userId, List<BookingStatus> statuses, Pageable pageable);

    // Single booking for its owner; 404 when absent OR owned by someone else
    // (no existence leak across users).
    BookingResponse getUserBookingById(String bookingId, Long userId);

    // Owner-initiated cancel; same 404 rule as getUserBookingById, then the
    // standard cancel semantics below. Returns the staged BOOKING_CANCELLED
    // outbox record (null when already cancelled) — the caller publishes it
    // AFTER this transaction commits, exactly like the failed flow.
    OutgoingOutboxRecord cancelUserBooking(String bookingId, Long userId, String cancelReason);


    // Derived state-history timeline for a single booking (see BookingTimelineEntry javadoc
    // for the "derived, not persisted" caveat).
    List<BookingTimelineEntry> getBookingEvents(String bookingId);


    // Dashboard KPI summary (counts by status, conversion rate, avg booking value).
    // from/to are optional; null means "all time".
    BookingMetricsResponse getBookingMetrics(LocalDateTime from, LocalDateTime to);


    // Update methods (admin triggered).
    // Cancels a PENDING or CONFIRMED booking and stages a BOOKING_CANCELLED
    // outbox record in the same transaction. Returns the staged record (null
    // when already CANCELLED) — the caller MUST publish it via
    // OutgoingOutboxPublisher.trySinglePublish AFTER commit (Kafka has no
    // notion of our DB tx; publishing inside could notify the platform of a
    // cancel that then rolls back). Same split as the failed flow
    // (InboundEventProcessorService stages inside, publishes outside).
    // Idempotent when already CANCELLED; 404 when absent; 409 when FAILED.
    // CONFIRMED cancels require a manual ops refund (see docs/cancellation).
    OutgoingOutboxRecord cancelBooking(String bookingId, String cancelReason);

    OutgoingOutboxRecord completeBooking(String bookingId);
}

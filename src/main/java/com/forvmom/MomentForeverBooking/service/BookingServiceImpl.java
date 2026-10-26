package com.forvmom.MomentForeverBooking.service;


import com.forvmom.MomentForeverBooking.commons.EventConstants;
import com.forvmom.MomentForeverBooking.commons.OutboundEventGenerator;
import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.domain.entity.OutgoingOutboxRecord;
import com.forvmom.MomentForeverBooking.domain.enums.BookingStatus;
import com.forvmom.MomentForeverBooking.dto.response.BookingMetricsResponse;
import com.forvmom.MomentForeverBooking.dto.response.BookingResponse;
import com.forvmom.MomentForeverBooking.dto.response.BookingTimelineEntry;
import com.forvmom.MomentForeverBooking.events.BookingCancelledEvent;
import com.forvmom.MomentForeverBooking.events.ExperienceCompletedEvent;
import com.forvmom.MomentForeverBooking.events.InboundEvent;
import com.forvmom.MomentForeverBooking.exception.BookingNotFoundException;
import com.forvmom.MomentForeverBooking.exception.BookingStatusConflictException;
import com.forvmom.MomentForeverBooking.mapper.BookingMapper;
import com.forvmom.MomentForeverBooking.repository.BookingRepository;
import com.forvmom.MomentForeverBooking.repository.search.BookingSearch;
import com.forvmom.MomentForeverBooking.service.inbound.InboundEventProcessorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
public class BookingServiceImpl implements BookingService {


    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);


    private final BookingRepository bookingRepository;
    private final BookingStatusTransitionService bookingStatusTransitionService;
    private final InboundEventProcessorRegistry processorRegistry; // if you still need it
    private final OutgoingOutboxService outgoingOutboxService;


    public BookingServiceImpl(BookingRepository bookingRepository,
                              BookingStatusTransitionService bookingStatusTransitionService,
                              InboundEventProcessorRegistry processorRegistry,
                              OutgoingOutboxService outgoingOutboxService) {
        this.bookingRepository = bookingRepository;
        this.bookingStatusTransitionService = bookingStatusTransitionService;
        this.processorRegistry = processorRegistry;
        this.outgoingOutboxService = outgoingOutboxService;
    }


    // ─── Inbound processing ─────────────────────────────────────────────
    @Override
    @Transactional
    public OutgoingOutboxRecord processBookingRequest(InboundEvent event) {
        // Delegate to the appropriate strategy via registry
        return processorRegistry.getProcessor(event.getEventType()).process(event).firstRecordOrNull();
    }


    // ─── Query methods ──────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingByBookingId(String bookingId) {
        // Plain fetch is enough: the DTO mapping below reads LAZY "addons"
        // while this transaction (and its session) is still open.
        Booking booking = bookingRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));
        return BookingMapper.toResponse(booking);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookingsByUser(Long userId, Pageable pageable) {
        Page<Booking> page = bookingRepository.findAllByUserId(userId, pageable);
        // Page.map preserves total elements/pages/sort — only content is converted.
        return page.map(BookingMapper::toResponse);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getAllBookings(List<BookingStatus> statuses, Long locationId, Long customerId,
                                        LocalDateTime from, LocalDateTime to, Pageable pageable) {
        // Entity rules (which filters, which fields) live in BookingSearch;
        // the generic engine renders absent filters as no-ops, so PostgreSQL
        // never receives an untyped null bind (the old 42P18 failure).
        Page<Booking> page = bookingRepository.findAll(
                BookingSearch.adminGrid(statuses, locationId, customerId, from, to),
                pageable);
        // DTO mapping inside the transaction: no proxies escape to Jackson.
        // NOTE: each row's LAZY addons load on access here (N+1, one query per
        // row, capped by page size ~10 — negligible for an admin grid). If the
        // grid ever grows (large pages, polling), reintroduce a batch fetch
        // (e.g. @EntityGraph ...In query) instead of a fetch-join on the paged
        // query, which would push pagination into memory (HHH90003004).
        return page.map(BookingMapper::toResponse);
    }


    // ─── User self-service ────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getUserBookings(Long userId, List<BookingStatus> statuses, Pageable pageable) {
        // Mandatory scope: a null userId would degrade the EQ filter to a
        // no-op and list EVERYONE's bookings — fail closed instead.
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        Page<Booking> page = bookingRepository.findAll(
                BookingSearch.userBookings(userId, statuses), pageable);
        return page.map(BookingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getUserBookingById(String bookingId, Long userId) {
        BookingResponse booking = getBookingByBookingId(bookingId);
        // Same 404 for "absent" and "someone else's": ownership failure must
        // not reveal whether the bookingId exists.
        if (userId == null || !userId.equals(booking.getUserId())) {
            throw new BookingNotFoundException("Booking not found: " + bookingId);
        }
        return booking;
    }

    @Override
    @Transactional
    public OutgoingOutboxRecord cancelUserBooking(String bookingId, Long userId, String cancelReason) {
        // Ownership check first (404 on mismatch), then standard cancel
        // semantics (stages the BOOKING_CANCELLED outbox event on a win).
        getUserBookingById(bookingId, userId);
        return cancelBooking(bookingId, cancelReason);
    }

    // ─── Timeline / events ───────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<BookingTimelineEntry> getBookingEvents(String bookingId) {
        // Reuses the DTO (same data, no extra query); the timeline only needs
        // scalar state fields, never the entity graph.
        BookingResponse booking = getBookingByBookingId(bookingId);
        List<BookingTimelineEntry> timeline = new ArrayList<>();


        LocalDateTime requestedAt = booking.getRequestedAt() != null ? booking.getRequestedAt() : booking.getCreatedAt();
        timeline.add(new BookingTimelineEntry(
                BookingStatus.PENDING.name(), "Booking requested, payment pending", requestedAt, null));


        if (booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.COMPLETED) {
            LocalDateTime confirmedAt = booking.getConfirmedAt() != null ? booking.getConfirmedAt() : booking.getUpdatedAt();
            timeline.add(new BookingTimelineEntry(
                    BookingStatus.CONFIRMED.name(), "Payment confirmed, booking confirmed", confirmedAt, null));
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            LocalDateTime completedAt = booking.getCompletedAt() != null
                    ? booking.getCompletedAt() : booking.getUpdatedAt();
            timeline.add(new BookingTimelineEntry(
                    BookingStatus.COMPLETED.name(), "Experience completed", completedAt, null));
        } else if (booking.getStatus() == BookingStatus.FAILED) {
            timeline.add(new BookingTimelineEntry(
                    BookingStatus.FAILED.name(), "Payment failed", booking.getUpdatedAt(), booking.getFailureReason()));
        } else if (booking.getStatus() == BookingStatus.CANCELLED) {
            timeline.add(new BookingTimelineEntry(
                    BookingStatus.CANCELLED.name(), "Booking cancelled", booking.getUpdatedAt(), null));
        }
        // PENDING: no further entries yet — still awaiting a payment outcome.


        return timeline;
    }


    // ─── Metrics ─────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public BookingMetricsResponse getBookingMetrics(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = bookingRepository.countByStatusInRange(from, to);


        long pending = 0;
        long confirmed = 0;
        long completed = 0;
        long failed = 0;
        long cancelled = 0;
        long total = 0;
        for (Object[] row : rows) {
            BookingStatus status = (BookingStatus) row[0];
            long count = (Long) row[1];
            total += count;
            switch (status) {
                case PENDING -> pending = count;
                case CONFIRMED -> confirmed = count;
                case COMPLETED -> completed = count;
                case FAILED -> failed = count;
                case CANCELLED -> cancelled = count;
            }
        }


        BigDecimal conversionRate = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(confirmed + completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);


        BigDecimal avgConfirmedValue = bookingRepository.averageConfirmedGrandTotalInRange(from, to);
        if (avgConfirmedValue != null) {
            avgConfirmedValue = avgConfirmedValue.setScale(2, RoundingMode.HALF_UP);
        }


        BookingMetricsResponse metrics = new BookingMetricsResponse();
        metrics.setFrom(from);
        metrics.setTo(to);
        metrics.setTotal(total);
        metrics.setPending(pending);
        metrics.setConfirmed(confirmed);
        metrics.setCompleted(completed);
        metrics.setFailed(failed);
        metrics.setCancelled(cancelled);
        metrics.setConversionRatePercent(conversionRate);
        metrics.setAverageConfirmedBookingValue(avgConfirmedValue);
        return metrics;
    }


    // ─── Admin update methods ───────────────────────────────────────────
    // Cancellation stages its outbox record in the same transaction as the
    // CAS claim (atomic: status row + event row commit together) and returns
    // it for the CALLER to publish after commit — the same split as the
    // failed flow (InboundEventProcessorService processes inside the TX,
    // trySinglePublish runs outside it). Double-cancel is idempotent: the
    // second call finds CANCELLED and stages nothing (so no duplicate outbox
    // row, and the uq(outgoing booking,event) constraint is never hit).
    @Override
    @Transactional
    public OutgoingOutboxRecord cancelBooking(String bookingId, String cancelReason) {
        // loadTransitionResult re-reads post-clear, so the won booking already
        // carries cancelReason/cancelledAt — no second fetch needed.
        Optional<Booking> won = bookingStatusTransitionService
                .transitionActiveBookingToCancelled(bookingId, cancelReason);
        if (won.isPresent()) {
            Booking booking = won.get();
            BookingCancelledEvent event = (BookingCancelledEvent)
                    OutboundEventGenerator.buildOutboundEvent(
                            booking, EventConstants.BOOKING_CANCELLED, null);
            OutgoingOutboxRecord record = outgoingOutboxService.createRecord(
                    bookingId, EventConstants.BOOKING_CANCELLED, event);
            log.info("Booking cancellation won and outbox staged: bookingId={}, outboxId={}",
                    bookingId, record.getId());
            return record;
        }


        Booking booking = bookingRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.info("Booking cancellation skipped idempotently: bookingId={}, currentStatus={}",
                    bookingId, booking.getStatus());
            return null;
        }
        // CAS lost: FAILED was never cancellable, and a still-active status
        // here means a concurrent transition (e.g. payment confirmation)
        // committed between our CAS UPDATE and this re-read — it won the
        // race, so cancelling now would overwrite the winner. 409 either way;
        // the caller can retry (a retry re-runs the CAS against fresh state).
        throw new BookingStatusConflictException(bookingId, booking.getStatus().name(), "cancel");
    }

    @Override
    @Transactional
    public OutgoingOutboxRecord completeBooking(String bookingId) {
        Optional<Booking> won = bookingStatusTransitionService
                .transitionConfirmedBookingToCompleted(bookingId);
        if (won.isPresent()) {
            Booking booking = won.get();
            ExperienceCompletedEvent event = (ExperienceCompletedEvent)
                    OutboundEventGenerator.buildOutboundEvent(
                            booking, EventConstants.EXPERIENCE_COMPLETED, null);
            OutgoingOutboxRecord record = outgoingOutboxService.createRecord(
                    bookingId, EventConstants.EXPERIENCE_COMPLETED, event);
            log.info("Booking completion won and outbox staged: bookingId={}, outboxId={}",
                    bookingId, record.getId());
            return record;
        }

        Booking booking = bookingRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            log.info("Booking completion skipped idempotently: bookingId={}", bookingId);
            return null;
        }
        throw new BookingStatusConflictException(bookingId, booking.getStatus().name(), "complete");
    }
}
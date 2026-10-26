package com.forvmom.MomentForeverBooking.service.inbound;

import com.forvmom.MomentForeverBooking.commons.EventConstants;
import com.forvmom.MomentForeverBooking.commons.OutboundEventGenerator;
import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.domain.entity.OutgoingOutboxRecord;
import com.forvmom.MomentForeverBooking.domain.enums.BookingStatus;
import com.forvmom.MomentForeverBooking.events.BookingConfirmedEvent;
import com.forvmom.MomentForeverBooking.events.InboundEvent;
import com.forvmom.MomentForeverBooking.events.ReceiptEligibilityCapturedEvent;
import com.forvmom.MomentForeverBooking.repository.BookingRepository;
import com.forvmom.MomentForeverBooking.service.BookingStatusTransitionService;
import com.forvmom.MomentForeverBooking.service.OutgoingOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class PaymentProcessedProcessor  implements InboundBookingEventProcessor  {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessedProcessor.class);


    private final BookingStatusTransitionService bookingStatusTransitionService;
    private final OutgoingOutboxService outgoingOutboxService;
    private final BookingRepository bookingRepository;

    public PaymentProcessedProcessor(
            BookingStatusTransitionService bookingStatusTransitionService,
            OutgoingOutboxService outgoingOutboxService,
            BookingRepository bookingRepository) {
        this.bookingStatusTransitionService = bookingStatusTransitionService;
        this.outgoingOutboxService = outgoingOutboxService;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public OutboundProcessingResult process(InboundEvent inboundEvent) {

        Booking booking = bookingStatusTransitionService
                .transitionPendingBookingToConfirmed(inboundEvent.getBookingId())
                .orElse(null);
        if (booking == null) {
            return OutboundProcessingResult.none();
        }

        OutboundProcessingResult result = createOutputs(
                booking,
                inboundEvent,
                Set.of(EventConstants.BOOKING_CONFIRMED, EventConstants.RECEIPT_ELIGIBILITY_CAPTURED));
        log.info("Booking confirmation outbox created: bookingId={}, eventType={}",
                inboundEvent.getBookingId(), EventConstants.BOOKING_CONFIRMED);
        return result;
    }

    @Override
    public OutboundProcessingResult reconcile(InboundEvent inboundEvent, Set<String> missingEventTypes) {
        if (missingEventTypes.isEmpty()) {
            return OutboundProcessingResult.none();
        }
        if (!Set.of(EventConstants.BOOKING_CONFIRMED, EventConstants.RECEIPT_ELIGIBILITY_CAPTURED)
                .containsAll(missingEventTypes)) {
            throw new IllegalArgumentException("Unsupported payment-processed outbox reconciliation");
        }

        Booking booking = bookingRepository.findByBookingId(inboundEvent.getBookingId())
                .orElseThrow(() -> new IllegalStateException("Booking is unavailable for outbox reconciliation"));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking is not confirmed for outbox reconciliation");
        }
        return createOutputs(booking, inboundEvent, missingEventTypes);
    }

    private OutboundProcessingResult createOutputs(
            Booking booking,
            InboundEvent inboundEvent,
            Set<String> eventTypes) {
        List<OutgoingOutboxRecord> records = new ArrayList<>();
        if (eventTypes.contains(EventConstants.BOOKING_CONFIRMED)) {
            BookingConfirmedEvent bookingConfirmedEvent = (BookingConfirmedEvent)
                    OutboundEventGenerator.buildOutboundEvent(
                            booking, EventConstants.BOOKING_CONFIRMED, inboundEvent);
            records.add(outgoingOutboxService.createRecord(
                    inboundEvent.getBookingId(), EventConstants.BOOKING_CONFIRMED, bookingConfirmedEvent));
        }
        if (eventTypes.contains(EventConstants.RECEIPT_ELIGIBILITY_CAPTURED)) {
            ReceiptEligibilityCapturedEvent receiptEligibilityEvent =
                    (ReceiptEligibilityCapturedEvent) OutboundEventGenerator.buildOutboundEvent(
                            booking, EventConstants.RECEIPT_ELIGIBILITY_CAPTURED, inboundEvent);
            records.add(outgoingOutboxService.createRecord(
                    inboundEvent.getBookingId(),
                    EventConstants.RECEIPT_ELIGIBILITY_CAPTURED,
                    receiptEligibilityEvent));
        }
        return new OutboundProcessingResult(records);
    }

    @Override
    public String getSupportedEventType() {
        return EventConstants.PAYMENT_PROCESSED;
    }
}

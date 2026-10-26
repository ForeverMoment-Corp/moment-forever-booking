package com.forvmom.MomentForeverBooking.commons;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.events.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public class OutboundEventGenerator {

    private static final String PRODUCER = "booking-service";
    private static final int SCHEMA_VERSION = 1;

    public static OutboundEvent buildOutboundEvent(Booking booking, String eventType, InboundEvent inboundEvent) {

        if (eventType != null) {
            if (eventType.equals(EventConstants.BOOKING_FAILED)) {
                return buildBookingFailedEvent(booking, inboundEvent);
            }

            if (eventType.equals(EventConstants.BOOKING_CONFIRMED)) {
                return buildBookingConfirmedEvent(booking, inboundEvent);
            }

            if (eventType.equals(EventConstants.BOOKING_CANCELLED)) {
                return buildBookingCancelledEvent(booking);
            }

            if (eventType.equals(EventConstants.RECEIPT_GENERATION_REQUESTED)) {
                return buildReceiptGenerationRequestedEvent(booking, inboundEvent);
            }

            if (eventType.equals(EventConstants.RECEIPT_ELIGIBILITY_CAPTURED)) {
                return buildReceiptEligibilityCapturedEvent(booking, inboundEvent);
            }

            if (eventType.equals(EventConstants.EXPERIENCE_COMPLETED)) {
                return buildExperienceCompletedEvent(booking);
            }

            if (eventType.equals(EventConstants.PAYMENT_REQUESTED)) {
                return buildPaymentRequestedEvent(booking, inboundEvent);
            }
        }
        throw new IllegalArgumentException("Unsupported event type: " + eventType);
    }

    private static OutboundEvent buildBookingConfirmedEvent(Booking booking, InboundEvent inboundEvent) {
        BookingConfirmedEvent e = new BookingConfirmedEvent();
        applyIdentity(e, EventConstants.BOOKING_CONFIRMED, booking.getBookingId(), inboundEvent);
        e.setBookingId(booking.getBookingId());
        e.setConfirmedAt(LocalDateTime.now());
        e.setUserId(booking.getUserId());
        e.setUserEmail(booking.getUserEmail());
        e.setUserFullName(booking.getUserFullName());
        e.setExperienceId(booking.getExperienceId());
        e.setExperienceName(booking.getExperienceName());
        e.setTimeSlotMapperId(booking.getTimeSlotMapperId());
        e.setGuestCount(booking.getGuestCount());
        e.setBookingDate(booking.getBookingDate());
        return e;
    }

    private static PaymentRequestedEvent buildPaymentRequestedEvent(Booking booking, InboundEvent inboundEvent) {
        PaymentRequestedEvent e = new PaymentRequestedEvent();
        applyIdentity(e, EventConstants.PAYMENT_REQUESTED, booking.getBookingId(), inboundEvent);
        e.setBookingId(booking.getBookingId());
        e.setRequestedAt(LocalDateTime.now());
        e.setGrandTotal(booking.getGrandTotal());
        e.setUserId(booking.getUserId());
        e.setUserEmail(booking.getUserEmail());
        e.setExperienceId(booking.getExperienceId());
        e.setExperienceName(booking.getExperienceName());
        e.setTimeSlotMapperId(booking.getTimeSlotMapperId());
        e.setGuestCount(booking.getGuestCount());
        e.setCurrency("INR");
        return e;
    }

    private static ReceiptEligibilityCapturedEvent buildReceiptEligibilityCapturedEvent(
            Booking booking,
            InboundEvent inboundEvent) {
        if (!(inboundEvent instanceof PaymentProcessedEvent paymentEvent)) {
            throw new IllegalArgumentException("Receipt eligibility requires payment evidence");
        }

        ReceiptEligibilityCapturedEvent event = new ReceiptEligibilityCapturedEvent();
        applyIdentity(event, EventConstants.RECEIPT_ELIGIBILITY_CAPTURED, booking.getBookingId(), inboundEvent);
        event.setBookingId(booking.getBookingId());
        event.setUserId(booking.getUserId());
        event.setUserEmail(booking.getUserEmail());
        event.setUserFullName(booking.getUserFullName());
        event.setExperienceName(booking.getExperienceName());
        if (booking.getBookingDate() != null) {
            event.setBookingDate(booking.getBookingDate().toInstant(java.time.ZoneOffset.UTC));
        }
        event.setExpectedAmount(booking.getGrandTotal());
        event.setExpectedCurrency("INR");
        event.setPaymentTransactionId(paymentEvent.getTransactionId());
        event.setAmountPaid(paymentEvent.getAmountPaid());
        event.setCurrencyPaid(paymentEvent.getCurrency());
        if (paymentEvent.getPaidAt() != null) {
            event.setPaidAt(paymentEvent.getPaidAt().toInstant(java.time.ZoneOffset.UTC));
        }
        return event;
    }

    private static ExperienceCompletedEvent buildExperienceCompletedEvent(Booking booking) {
        ExperienceCompletedEvent event = new ExperienceCompletedEvent();
        applyIdentity(event, EventConstants.EXPERIENCE_COMPLETED, booking.getBookingId(),
                booking.getBookingId(), null);
        event.setBookingId(booking.getBookingId());
        event.setUserId(booking.getUserId());
        event.setExperienceId(booking.getExperienceId());
        if (booking.getCompletedAt() == null) {
            throw new IllegalStateException("Completed booking must have a completion time");
        }
        event.setCompletedAt(booking.getCompletedAt().toInstant(java.time.ZoneOffset.UTC));
        return event;
    }

    private static ReceiptGenerationRequestedEvent buildReceiptGenerationRequestedEvent(
            Booking booking,
            InboundEvent inboundEvent) {
        if (!(inboundEvent instanceof PaymentProcessedEvent paymentEvent)) {
            throw new IllegalArgumentException("Receipt generation requires payment evidence");
        }

        ReceiptGenerationRequestedEvent event = new ReceiptGenerationRequestedEvent();
        applyIdentity(event, EventConstants.RECEIPT_GENERATION_REQUESTED, booking.getBookingId(), inboundEvent);
        event.setBookingId(booking.getBookingId());
        event.setUserId(booking.getUserId());
        event.setUserEmail(booking.getUserEmail());
        event.setUserFullName(booking.getUserFullName());
        event.setExperienceName(booking.getExperienceName());
        if (booking.getBookingDate() != null) {
            event.setBookingDate(booking.getBookingDate().toInstant(java.time.ZoneOffset.UTC));
        }
        event.setExpectedAmount(booking.getGrandTotal());
        event.setExpectedCurrency("INR");
        event.setPaymentTransactionId(paymentEvent.getTransactionId());
        event.setAmountPaid(paymentEvent.getAmountPaid());
        event.setCurrencyPaid(paymentEvent.getCurrency());
        if (paymentEvent.getPaidAt() != null) {
            event.setPaidAt(paymentEvent.getPaidAt().toInstant(java.time.ZoneOffset.UTC));
        }
        return event;
    }

    private static BookingFailedEvent buildBookingFailedEvent(Booking booking, InboundEvent inboundEvent) {

        if(booking==null){
            BookingFailedEvent bookingFailedEvent = new BookingFailedEvent();
            applyIdentity(bookingFailedEvent, EventConstants.BOOKING_FAILED,
                    inboundEvent.getBookingId(), inboundEvent);
            bookingFailedEvent.setBookingId(inboundEvent.getBookingId());
            if (inboundEvent instanceof BookingRequestEvent requestEvent) {
                bookingFailedEvent.setUserId(requestEvent.getUserId());
                bookingFailedEvent.setUserEmail(requestEvent.getUserEmail());
                bookingFailedEvent.setExperienceId(requestEvent.getExperienceId());
                bookingFailedEvent.setTimeSlotMapperId(requestEvent.getTimeSlotMapperId());
                bookingFailedEvent.setGuestCount(requestEvent.getGuestCount());
                bookingFailedEvent.setBookingDate(requestEvent.getBookingDate());
            }
            bookingFailedEvent.setFailedAt(LocalDateTime.now());
            bookingFailedEvent.setFailureReason("Booking not found for id: " + inboundEvent.getBookingId());
            return bookingFailedEvent;
        }
        BookingFailedEvent bookingFailedEvent = new BookingFailedEvent();
        applyIdentity(bookingFailedEvent, EventConstants.BOOKING_FAILED, booking.getBookingId(), inboundEvent);
        bookingFailedEvent.setBookingId(booking.getBookingId());
        bookingFailedEvent.setFailedAt(LocalDateTime.now());
        bookingFailedEvent.setFailureReason("TO BE FILLED BY CALLER");
        bookingFailedEvent.setUserId(booking.getUserId());
        bookingFailedEvent.setUserEmail(booking.getUserEmail());
        bookingFailedEvent.setExperienceId(booking.getExperienceId());
        bookingFailedEvent.setTimeSlotMapperId(booking.getTimeSlotMapperId());
        bookingFailedEvent.setGuestCount(booking.getGuestCount());
        if (booking.getBookingDate() != null) {
            bookingFailedEvent.setBookingDate(booking.getBookingDate().toLocalDate());
        }
        return bookingFailedEvent;
    }

    /**
     * Builds the cancellation event from the freshly-cancelled booking row.
     * Unlike failure/confirmation flows there is no inbound Kafka event that
     * triggered the cancel (it is HTTP-initiated), so identity uses the
     * bookingId as correlation and no causation id.
     */
    public static BookingCancelledEvent buildBookingCancelledEvent(Booking booking) {
        BookingCancelledEvent event = new BookingCancelledEvent();
        applyIdentity(event, EventConstants.BOOKING_CANCELLED, booking.getBookingId(),
                booking.getBookingId(), null);
        event.setBookingId(booking.getBookingId());
        event.setUserId(booking.getUserId());
        event.setUserEmail(booking.getUserEmail());
        event.setUserFullName(booking.getUserFullName());
        event.setExperienceId(booking.getExperienceId());
        event.setExperienceName(booking.getExperienceName());
        event.setTimeSlotMapperId(booking.getTimeSlotMapperId());
        event.setGuestCount(booking.getGuestCount());
        if (booking.getBookingDate() != null) {
            event.setBookingDate(booking.getBookingDate().toLocalDate());
        }
        event.setCancelReason(booking.getCancelReason());
        event.setCancelledAt(booking.getCancelledAt() != null
                ? booking.getCancelledAt() : LocalDateTime.now());
        event.setConfirmedAt(booking.getConfirmedAt());
        return event;
    }

    public static BookingFailedEvent buildDeadPaymentRequestFailure(            Booking booking,
            String failureReason,
            String causationId) {
        BookingFailedEvent event = new BookingFailedEvent();
        applyIdentity(event, EventConstants.BOOKING_FAILED, booking.getBookingId(),
                booking.getBookingId(), causationId);
        event.setBookingId(booking.getBookingId());
        event.setFailedAt(LocalDateTime.now());
        event.setFailureReason(failureReason);
        event.setUserId(booking.getUserId());
        event.setUserEmail(booking.getUserEmail());
        event.setExperienceId(booking.getExperienceId());
        event.setTimeSlotMapperId(booking.getTimeSlotMapperId());
        event.setGuestCount(booking.getGuestCount());
        if (booking.getBookingDate() != null) {
            event.setBookingDate(booking.getBookingDate().toLocalDate());
        }
        return event;
    }

    private static void applyIdentity(BaseEvent event, String eventType, String bookingId,
                                      InboundEvent inboundEvent) {
        applyIdentity(event, eventType, bookingId,
                inboundEvent.getCorrelationId(), inboundEvent.getEventId());
    }

    private static void applyIdentity(BaseEvent event, String eventType, String bookingId,
                                      String correlationId, String causationId) {
        Instant now = Instant.now();
        event.setEventId(UUID.randomUUID().toString());
        event.setProducer(PRODUCER);
        event.setSchemaVersion(SCHEMA_VERSION);
        event.setOccurredAt(now);
        event.setEventType(eventType);
        event.setCorrelationId(correlationId != null
                ? correlationId
                : bookingId);
        event.setCausationId(causationId);
    }
}

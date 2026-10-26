package com.forvmom.MomentForeverBooking.service.inbound;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.domain.entity.OutgoingOutboxRecord;
import com.forvmom.MomentForeverBooking.domain.enums.BookingStatus;
import com.forvmom.MomentForeverBooking.events.PaymentFailedEvent;
import com.forvmom.MomentForeverBooking.events.PaymentProcessedEvent;
import com.forvmom.MomentForeverBooking.events.ReceiptEligibilityCapturedEvent;
import com.forvmom.MomentForeverBooking.repository.BookingRepository;
import com.forvmom.MomentForeverBooking.service.BookingStatusTransitionService;
import com.forvmom.MomentForeverBooking.service.OutgoingOutboxService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

class PaymentProcessorFailurePropagationTest {

    @Test
    void paymentProcessedOutgoingOutboxFailurePropagates() {
        BookingStatusTransitionService transitionService = mock(BookingStatusTransitionService.class);
        OutgoingOutboxService outgoingOutboxService = mock(OutgoingOutboxService.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentProcessedProcessor processor =
                new PaymentProcessedProcessor(transitionService, outgoingOutboxService, bookingRepository);
        PaymentProcessedEvent event = new PaymentProcessedEvent();
        event.setBookingId("B-100");
        Booking booking = pendingBooking();
        RuntimeException outboxFailure = new RuntimeException("outbox unavailable");
        when(transitionService.transitionPendingBookingToConfirmed("B-100"))
                .thenReturn(Optional.of(booking));
        when(outgoingOutboxService.createRecord(anyString(), anyString(), any()))
                .thenThrow(outboxFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> processor.process(event));

        assertSame(outboxFailure, thrown);
        verify(outgoingOutboxService, never()).markAsFailed(any());
    }

    @Test
    void paymentFailedOutgoingOutboxFailurePropagates() {
        BookingStatusTransitionService transitionService = mock(BookingStatusTransitionService.class);
        OutgoingOutboxService outgoingOutboxService = mock(OutgoingOutboxService.class);
        PaymentFailedProcessor processor =
                new PaymentFailedProcessor(transitionService, outgoingOutboxService);
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setBookingId("B-100");
        Booking booking = pendingBooking();
        RuntimeException outboxFailure = new RuntimeException("outbox unavailable");
        when(transitionService.transitionPendingBookingToFailed("B-100", "Payment failed"))
                .thenReturn(Optional.of(booking));
        when(outgoingOutboxService.createRecord(anyString(), anyString(), any()))
                .thenThrow(outboxFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> processor.process(event));

        assertSame(outboxFailure, thrown);
        verify(outgoingOutboxService, never()).markAsFailed(any());
    }

    @Test
    void paymentProcessedCreatesIndependentEligibilityOutboxWithPaymentEvidence() {
        BookingStatusTransitionService transitionService = mock(BookingStatusTransitionService.class);
        OutgoingOutboxService outgoingOutboxService = mock(OutgoingOutboxService.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentProcessedProcessor processor =
                new PaymentProcessedProcessor(transitionService, outgoingOutboxService, bookingRepository);
        Booking booking = pendingBooking();
        booking.setUserId(42L);
        booking.setUserEmail("guest@example.com");
        booking.setUserFullName("Guest");
        booking.setExperienceName("Experience");
        booking.setBookingDate(LocalDateTime.of(2030, 1, 15, 9, 0));
        booking.setGrandTotal(new BigDecimal("200.00"));
        PaymentProcessedEvent event = new PaymentProcessedEvent();
        event.setBookingId("B-100");
        event.setEventId("payment-100");
        event.setProducer("payment-service");
        event.setCorrelationId("B-100");
        event.setTransactionId("txn-100");
        event.setAmountPaid(new BigDecimal("200.00"));
        event.setCurrency("INR");
        event.setPaidAt(LocalDateTime.of(2026, 10, 18, 10, 0));
        when(transitionService.transitionPendingBookingToConfirmed("B-100"))
                .thenReturn(Optional.of(booking));
        when(outgoingOutboxService.createRecord(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    OutgoingOutboxRecord record = new OutgoingOutboxRecord();
                    record.setBookingId(invocation.getArgument(0));
                    record.setEventType(invocation.getArgument(1));
                    return record;
                });

        OutboundProcessingResult result = processor.process(event);

        org.mockito.ArgumentCaptor<ReceiptEligibilityCapturedEvent> receiptEvent =
                org.mockito.ArgumentCaptor.forClass(ReceiptEligibilityCapturedEvent.class);
        verify(outgoingOutboxService).createRecord(
                eq("B-100"), eq("BOOKING_CONFIRMED"), any());
        verify(outgoingOutboxService).createRecord(
                eq("B-100"), eq("RECEIPT_ELIGIBILITY_CAPTURED"), receiptEvent.capture());
        verify(outgoingOutboxService, times(2)).createRecord(anyString(), anyString(), any());
        assertEquals(2, result.records().size());
        assertEquals("txn-100", receiptEvent.getValue().getPaymentTransactionId());
        assertEquals(new BigDecimal("200.00"), receiptEvent.getValue().getAmountPaid());
        assertEquals("INR", receiptEvent.getValue().getCurrencyPaid());
        assertEquals(new BigDecimal("200.00"), receiptEvent.getValue().getExpectedAmount());
    }

    private Booking pendingBooking() {
        Booking booking = new Booking();
        booking.setBookingId("B-100");
        booking.setStatus(BookingStatus.PENDING);
        return booking;
    }
}

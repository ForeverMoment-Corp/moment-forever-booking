package com.forvmom.MomentForeverBooking.service.retries_cleanup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forvmom.MomentForeverBooking.commons.EventConstants;
import com.forvmom.MomentForeverBooking.domain.entity.InboundOutbox;
import com.forvmom.MomentForeverBooking.domain.entity.OutgoingOutboxRecord;
import com.forvmom.MomentForeverBooking.events.PaymentProcessedEvent;
import com.forvmom.MomentForeverBooking.repository.InboundOutboxDao;
import com.forvmom.MomentForeverBooking.repository.OutgoingOutboxDao;
import com.forvmom.MomentForeverBooking.service.InboundOutboxService;
import com.forvmom.MomentForeverBooking.service.inbound.InboundEventProcessingTransactionService;
import com.forvmom.MomentForeverBooking.service.inbound.OutboundProcessingResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboundOutboxRetryServiceTest {

    @Test
    void missingPaymentOutputsUseAtomicReconciliationAndPublishBothRecords() throws Exception {
        RetryFixture fixture = retryFixture();
        InboundOutbox inboundOutbox = failedInboundOutbox(fixture.objectMapper);
        OutgoingOutboxRecord confirmation = outgoingRecord(
                EventConstants.BOOKING_CONFIRMED, OutgoingOutboxRecord.STATUS_PENDING);
        OutgoingOutboxRecord eligibility = outgoingRecord(
                EventConstants.RECEIPT_ELIGIBILITY_CAPTURED, OutgoingOutboxRecord.STATUS_PENDING);
        when(fixture.inboundOutboxDao.findByStatusInAndUpdatedAtBefore(
                any(), any(LocalDateTime.class))).thenReturn(List.of(inboundOutbox));
        when(fixture.outgoingOutboxDao.findByBookingIdAndEventType(
                "B-100", EventConstants.BOOKING_CONFIRMED)).thenReturn(Optional.empty());
        when(fixture.outgoingOutboxDao.findByBookingIdAndEventType(
                "B-100", EventConstants.RECEIPT_ELIGIBILITY_CAPTURED)).thenReturn(Optional.empty());
        when(fixture.transactionService.reconcileInboundEventAtomically(
                org.mockito.ArgumentMatchers.eq(inboundOutbox),
                any(PaymentProcessedEvent.class),
                org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(OutboundProcessingResult.of(confirmation, eligibility));

        fixture.retryService.retryStuckAndFailedRecords();

        verify(fixture.transactionService).reconcileInboundEventAtomically(
                org.mockito.ArgumentMatchers.eq(inboundOutbox),
                any(PaymentProcessedEvent.class),
                org.mockito.ArgumentMatchers.eq(java.util.Set.of(
                        EventConstants.BOOKING_CONFIRMED,
                        EventConstants.RECEIPT_ELIGIBILITY_CAPTURED)));
        verify(fixture.outgoingOutboxPublisher).trySinglePublish(confirmation);
        verify(fixture.outgoingOutboxPublisher).trySinglePublish(eligibility);
    }

    @Test
    void existingSentOutgoingMarksInboundProcessedAndSkipsPublish() throws Exception {
        RetryFixture fixture = retryFixture();
        InboundOutbox inboundOutbox = failedInboundOutbox(fixture.objectMapper);
        OutgoingOutboxRecord confirmation = outgoingRecord(
                EventConstants.BOOKING_CONFIRMED, OutgoingOutboxRecord.STATUS_SENT);
        OutgoingOutboxRecord eligibility = outgoingRecord(
                EventConstants.RECEIPT_ELIGIBILITY_CAPTURED, OutgoingOutboxRecord.STATUS_SENT);
        when(fixture.inboundOutboxDao.findByStatusInAndUpdatedAtBefore(
                any(), any(LocalDateTime.class))).thenReturn(List.of(inboundOutbox));
        when(fixture.outgoingOutboxDao.findByBookingIdAndEventType(
                "B-100", EventConstants.BOOKING_CONFIRMED))
                .thenReturn(Optional.of(confirmation));
        when(fixture.outgoingOutboxDao.findByBookingIdAndEventType(
                "B-100", EventConstants.RECEIPT_ELIGIBILITY_CAPTURED))
                .thenReturn(Optional.of(eligibility));

        fixture.retryService.retryStuckAndFailedRecords();

        verify(fixture.transactionService).markInboundEventAsProcessed(inboundOutbox);
        verify(fixture.transactionService, never()).reconcileInboundEventAtomically(
                org.mockito.ArgumentMatchers.any(InboundOutbox.class),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anySet());
        verify(fixture.outgoingOutboxPublisher, never()).trySinglePublish(confirmation);
        verify(fixture.outgoingOutboxPublisher, never()).trySinglePublish(eligibility);
    }

    private RetryFixture retryFixture() {
        InboundOutboxDao inboundOutboxDao = mock(InboundOutboxDao.class);
        InboundOutboxService inboundOutboxService = mock(InboundOutboxService.class);
        OutboxDeadLetterHandler deadLetterHandler = mock(OutboxDeadLetterHandler.class);
        OutgoingOutboxDao outgoingOutboxDao = mock(OutgoingOutboxDao.class);
        OutgoingOutboxPublisher outgoingOutboxPublisher = mock(OutgoingOutboxPublisher.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        InboundEventProcessingTransactionService transactionService =
                mock(InboundEventProcessingTransactionService.class);
        InboundOutboxRetryService retryService = new InboundOutboxRetryService(
                inboundOutboxDao,
                inboundOutboxService,
                deadLetterHandler,
                outgoingOutboxDao,
                outgoingOutboxPublisher,
                objectMapper,
                transactionService);
        return new RetryFixture(
                inboundOutboxDao,
                outgoingOutboxDao,
                outgoingOutboxPublisher,
                objectMapper,
                transactionService,
                retryService);
    }

    private InboundOutbox failedInboundOutbox(ObjectMapper objectMapper) throws Exception {
        PaymentProcessedEvent event = new PaymentProcessedEvent();
        event.setBookingId("B-100");
        event.setEventType(EventConstants.PAYMENT_PROCESSED);
        InboundOutbox inboundOutbox = new InboundOutbox();
        inboundOutbox.setBookingReferenceId("B-100");
        inboundOutbox.setEventType(EventConstants.PAYMENT_PROCESSED);
        inboundOutbox.setPayload(objectMapper.writeValueAsString(event));
        inboundOutbox.setStatus(EventConstants.FAILED);
        return inboundOutbox;
    }

    private OutgoingOutboxRecord outgoingRecord(String eventType, String status) {
        OutgoingOutboxRecord outgoingOutboxRecord = new OutgoingOutboxRecord();
        outgoingOutboxRecord.setBookingId("B-100");
        outgoingOutboxRecord.setEventType(eventType);
        outgoingOutboxRecord.setStatus(status);
        return outgoingOutboxRecord;
    }
}

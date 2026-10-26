package com.forvmom.MomentForeverBooking.service.retries_cleanup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forvmom.MomentForeverBooking.commons.EventConstants;
import com.forvmom.MomentForeverBooking.domain.entity.InboundOutbox;
import com.forvmom.MomentForeverBooking.domain.entity.OutgoingOutboxRecord;
import com.forvmom.MomentForeverBooking.events.*;
import com.forvmom.MomentForeverBooking.repository.InboundOutboxDao;
import com.forvmom.MomentForeverBooking.repository.OutgoingOutboxDao;
import com.forvmom.MomentForeverBooking.service.InboundOutboxService;
import com.forvmom.MomentForeverBooking.service.inbound.InboundEventProcessingTransactionService;
import com.forvmom.MomentForeverBooking.service.inbound.OutboundProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retries failed inbound records by completing committed work or atomically
 * reprocessing rolled-back work.
 */
@Service
public class InboundOutboxRetryService {

    private static final Logger log = LoggerFactory.getLogger(InboundOutboxRetryService.class);
    private static final int MAX_RETRIES = 5;
    private static final long GRACE_PERIOD_MINUTES = 2;


    private static final Map<String, List<String>> OUTGOING_EVENT_TYPE_MAP = Map.of(
            EventConstants.BOOKING_REQUESTED, List.of(EventConstants.PAYMENT_REQUESTED),
            EventConstants.PAYMENT_PROCESSED, List.of(
                    EventConstants.BOOKING_CONFIRMED,
                    EventConstants.RECEIPT_ELIGIBILITY_CAPTURED),
            EventConstants.PAYMENT_FAILED, List.of(EventConstants.BOOKING_FAILED)
    );

    // Mapping from inbound event type to the corresponding event class for deserialization
    private static final Map<String, Class<? extends InboundEvent>> EVENT_CLASS_MAP = Map.of(
            EventConstants.BOOKING_REQUESTED, BookingRequestEvent.class,
            EventConstants.PAYMENT_PROCESSED, PaymentProcessedEvent.class,
            EventConstants.PAYMENT_FAILED, PaymentFailedEvent.class
    );

    private final InboundOutboxDao inboundOutboxDao;
    private final InboundOutboxService inboundOutboxService;
    private final OutboxDeadLetterHandler deadLetterHandler;
    private final OutgoingOutboxDao outgoingOutboxDao;
    private final OutgoingOutboxPublisher outgoingOutboxPublisher;
    private final ObjectMapper objectMapper;
    private final InboundEventProcessingTransactionService inboundEventProcessingTransactionService;

    public InboundOutboxRetryService(InboundOutboxDao inboundOutboxDao,
                                     InboundOutboxService inboundOutboxService,
                                     OutboxDeadLetterHandler deadLetterHandler,
                                     OutgoingOutboxDao outgoingOutboxDao,
                                     OutgoingOutboxPublisher outgoingOutboxPublisher,
                                     ObjectMapper objectMapper,
                                     InboundEventProcessingTransactionService
                                             inboundEventProcessingTransactionService) {

        this.inboundOutboxDao = inboundOutboxDao;
        this.inboundOutboxService = inboundOutboxService;
        this.deadLetterHandler = deadLetterHandler;
        this.outgoingOutboxDao = outgoingOutboxDao;
        this.outgoingOutboxPublisher = outgoingOutboxPublisher;
        this.objectMapper = objectMapper;
        this.inboundEventProcessingTransactionService = inboundEventProcessingTransactionService;
    }

    /**
     * Called by Quartz every 2 minutes.
     * Finds incoming inboundOutbox records stuck in PROCESSING or FAILED and retries them.
     */
    public void retryStuckAndFailedRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(GRACE_PERIOD_MINUTES);

        // check if it is incoming or outgoing record stuck, if incoming, we have to
        // re-run the enrichment and publish flow, if outgoing, we only need to
        // re-publish, no enrichment needed as it is already enriched before

        List<InboundOutbox> stuck = inboundOutboxDao.findByStatusInAndUpdatedAtBefore(
                List.of(EventConstants.PENDING, EventConstants.FAILED, EventConstants.PROCESSING),
                cutoff);

        if (!stuck.isEmpty()) {
            log.info("Incoming inboundOutbox poller found {} stuck record(s) to retry", stuck.size());
        }

        for (InboundOutbox inboundOutbox : stuck) {
            if (inboundOutbox.getRetryCount() >= MAX_RETRIES) {
                deadLetterHandler.handleDeadRecord(inboundOutbox);
                continue;
            }
            inboundOutboxService.incrementRetry(inboundOutbox);
            String bookingId = inboundOutbox.getBookingReferenceId();
            String eventType = inboundOutbox.getEventType();

            // Determine expected outgoing event type
            List<String> outgoingEventTypes = OUTGOING_EVENT_TYPE_MAP.get(eventType);
            if (outgoingEventTypes == null) {
                log.warn("No outgoing event mapping for event type: {}", eventType);
                inboundOutboxService.markAsFailed(inboundOutbox);
                continue;
            }
            try {
                List<OutgoingOutboxRecord> outgoingRecords = handleIncomingEventRetry(
                        inboundOutbox, bookingId, eventType, outgoingEventTypes);
                outgoingRecords.stream()
                        .filter(record -> !OutgoingOutboxRecord.STATUS_SENT.equals(record.getStatus()))
                        .forEach(outgoingOutboxPublisher::trySinglePublish);
            } catch (Exception e) {
                log.warn("Retry failed for bookingId={}, eventType={}, error: {}", bookingId, eventType, e.getMessage());
                inboundOutboxService.markAsFailed(inboundOutbox);
            }
        }
    }

    private List<OutgoingOutboxRecord> handleIncomingEventRetry(
            InboundOutbox inboundOutbox,
            String bookingId,
            String eventType,
            List<String> expectedEventTypes) throws JsonProcessingException {
        Map<String, OutgoingOutboxRecord> recordsByType = new LinkedHashMap<>();
        Set<String> missingEventTypes = new LinkedHashSet<>();
        for (String expectedEventType : expectedEventTypes) {
            outgoingOutboxDao.findByBookingIdAndEventType(bookingId, expectedEventType)
                    .ifPresentOrElse(
                            record -> recordsByType.put(expectedEventType, record),
                            () -> missingEventTypes.add(expectedEventType));
        }

        if (missingEventTypes.isEmpty()) {
            inboundEventProcessingTransactionService.markInboundEventAsProcessed(inboundOutbox);
            return recordsInExpectedOrder(recordsByType, expectedEventTypes);
        }

        Class<? extends InboundEvent> inboundEventClass = EVENT_CLASS_MAP.get(eventType);
        if (inboundEventClass == null) {
            throw new IllegalArgumentException("No inbound event class mapping for event type: " + eventType);
        }
        InboundEvent inboundEvent = objectMapper.readValue(inboundOutbox.getPayload(), inboundEventClass);
        OutboundProcessingResult restored = inboundEventProcessingTransactionService
                .reconcileInboundEventAtomically(inboundOutbox, inboundEvent, missingEventTypes);
        restored.records().forEach(record -> recordsByType.put(record.getEventType(), record));
        if (!recordsByType.keySet().containsAll(expectedEventTypes)) {
            throw new IllegalStateException("Required outgoing records are still missing after retry");
        }
        return recordsInExpectedOrder(recordsByType, expectedEventTypes);
    }

    private List<OutgoingOutboxRecord> recordsInExpectedOrder(
            Map<String, OutgoingOutboxRecord> recordsByType,
            List<String> expectedEventTypes) {
        List<OutgoingOutboxRecord> records = new ArrayList<>();
        for (String expectedEventType : expectedEventTypes) {
            OutgoingOutboxRecord record = recordsByType.get(expectedEventType);
            if (record == null) {
                throw new IllegalStateException("Required outgoing record is missing: " + expectedEventType);
            }
            records.add(record);
        }
        return records;
    }
}
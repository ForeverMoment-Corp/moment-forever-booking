package com.forvmom.MomentForeverBooking.service.inbound;

import com.forvmom.MomentForeverBooking.domain.entity.InboundOutbox;
import com.forvmom.MomentForeverBooking.events.InboundEvent;
import com.forvmom.MomentForeverBooking.service.InboundOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coordinates atomic inbound processing. Kafka publication is outside this
 * transaction; the Booking mutation, outgoing outbox write, and inbound
 * completion commit or roll back together.
 */
@Service
public class InboundEventProcessingTransactionService {

    private final InboundEventProcessorRegistry inboundEventProcessorRegistry;
    private final InboundOutboxService inboundOutboxService;

    public InboundEventProcessingTransactionService(
            InboundEventProcessorRegistry inboundEventProcessorRegistry,
            InboundOutboxService inboundOutboxService) {
        this.inboundEventProcessorRegistry = inboundEventProcessorRegistry;
        this.inboundOutboxService = inboundOutboxService;
    }

    /**
     * Processes an inbound event without publishing to Kafka. The Booking
     * mutation, outgoing outbox write, and exact inbound completion are
     * all-or-nothing within this transaction.
     */
    @Transactional
    public OutboundProcessingResult processInboundEventAtomically(
            InboundOutbox inboundOutbox,
            InboundEvent inboundEvent) {
        InboundBookingEventProcessor inboundEventProcessor =
                inboundEventProcessorRegistry.getProcessor(inboundEvent.getEventType());
        OutboundProcessingResult result = inboundEventProcessor.process(inboundEvent);
        inboundOutboxService.markAsProcessed(inboundOutbox);
        return result;
    }

    @Transactional
    public OutboundProcessingResult reconcileInboundEventAtomically(
            InboundOutbox inboundOutbox,
            InboundEvent inboundEvent,
            Set<String> missingEventTypes) {
        InboundBookingEventProcessor inboundEventProcessor =
                inboundEventProcessorRegistry.getProcessor(inboundEvent.getEventType());
        OutboundProcessingResult result = inboundEventProcessor.reconcile(inboundEvent, missingEventTypes);
        Set<String> createdEventTypes = result.records().stream()
                .map(record -> record.getEventType())
                .collect(Collectors.toSet());
        if (!createdEventTypes.containsAll(missingEventTypes)) {
            throw new IllegalStateException("Inbound retry did not restore every required outgoing record");
        }
        inboundOutboxService.markAsProcessed(inboundOutbox);
        return result;
    }

    @Transactional
    public void markInboundEventAsProcessed(InboundOutbox inboundOutbox) {
        inboundOutboxService.markAsProcessed(inboundOutbox);
    }
}

package com.forvmom.MomentForeverBooking.service.inbound;

import com.forvmom.MomentForeverBooking.events.InboundEvent;

import java.util.Set;

public interface InboundBookingEventProcessor {
    OutboundProcessingResult process(InboundEvent event);

    default OutboundProcessingResult reconcile(InboundEvent event, Set<String> missingEventTypes) {
        return process(event);
    }

    String getSupportedEventType();  // each strategy declares which event type it handles
}

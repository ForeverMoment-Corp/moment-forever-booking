package com.forvmom.MomentForeverBooking.consumer;

import com.forvmom.MomentForeverBooking.events.BookingConfirmedEvent;
import com.forvmom.MomentForeverBooking.service.notification.LifecycleNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Self-consumes the booking service's own {@code booking-confirmed} topic to
 * send the customer a "your booking is confirmed" email asynchronously —
 * decoupled from the request thread that updates the booking status.
 *
 * <p>
 * Ack discipline is intentionally simpler than {@code InboundEventProcessorService}:
 * this listener acks ONLY after the email is sent successfully. On failure it does
 * NOT ack, so the shared {@code kafkaListenerContainerFactory} error handler retries
 * delivery (3x, 1s apart) and then routes the record to the dead-letter topic. No
 * separate inbound-outbox/idempotency record is used here because a duplicate
 * confirmation email on redelivery is a minor UX nit, not a data-integrity issue —
 * unlike booking/payment state transitions, which do require exactly-once handling.
 *
 * <p>
 * This consumer is only activated when {@code spring.mail.username} is set,
 * since it depends on the conditional {@link EmailNotificationService}.
 */
@Component
public class BookingNotificationConsumer {


    private static final Logger log = LoggerFactory.getLogger(BookingNotificationConsumer.class);

    private final LifecycleNotificationService lifecycleNotificationService;

    public BookingNotificationConsumer(LifecycleNotificationService lifecycleNotificationService) {
        this.lifecycleNotificationService = lifecycleNotificationService;
    }

    @KafkaListener(topics = "${kafka.topics.booking-confirmed}", groupId = "booking-notification-group", containerFactory = "kafkaListenerContainerFactory")
    public void onBookingConfirmed(@Payload BookingConfirmedEvent event, Acknowledgment ack) {
        log.info("Dispatching booking-confirmed lifecycle notifications: bookingId={}", event.getBookingId());
        lifecycleNotificationService.notifyConfirmed(event);
        ack.acknowledge();
    }
}

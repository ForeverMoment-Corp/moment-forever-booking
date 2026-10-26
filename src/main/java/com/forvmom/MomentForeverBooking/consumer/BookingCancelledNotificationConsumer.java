package com.forvmom.MomentForeverBooking.consumer;

import com.forvmom.MomentForeverBooking.events.BookingCancelledEvent;
import com.forvmom.MomentForeverBooking.service.notification.LifecycleNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Self-consumes the booking service's own {@code booking-cancelled} topic to
 * send the customer a "your booking is cancelled" email asynchronously —
 * mirroring {@link BookingNotificationConsumer} (booking-confirmed).
 *
 * <p>Same ack discipline: ack ONLY after the email is sent. On failure the
 * record is NOT acked, so the shared error handler retries (3x, 1s apart)
 * and then routes to the dead-letter topic. A duplicate cancellation email on
 * redelivery is acceptable (UX nit, not a state issue).
 *
 * <p>Only activated when {@code spring.mail.username} is set, since it
 * depends on the conditional {@link EmailNotificationService}.
 */
@Component
public class BookingCancelledNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingCancelledNotificationConsumer.class);

    private final LifecycleNotificationService lifecycleNotificationService;

    public BookingCancelledNotificationConsumer(LifecycleNotificationService lifecycleNotificationService) {
        this.lifecycleNotificationService = lifecycleNotificationService;
    }

    @KafkaListener(topics = "${kafka.topics.booking-cancelled}", groupId = "booking-notification-group", containerFactory = "kafkaListenerContainerFactory")
    public void onBookingCancelled(@Payload BookingCancelledEvent event, Acknowledgment ack) {
        log.info("Dispatching booking-cancelled lifecycle notifications: bookingId={}", event.getBookingId());
        lifecycleNotificationService.notifyCancelled(event);
        ack.acknowledge();
    }
}

package com.forvmom.MomentForeverBooking.consumer;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.events.BookingConfirmedEvent;
import com.forvmom.MomentForeverBooking.repository.BookingRepository;
import com.forvmom.MomentForeverBooking.service.notification.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "spring.mail.username")
public class BookingNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationConsumer.class);

    private final BookingRepository bookingRepository;
    private final EmailNotificationService emailNotificationService;

    public BookingNotificationConsumer(BookingRepository bookingRepository,
                                        EmailNotificationService emailNotificationService) {
        this.bookingRepository = bookingRepository;
        this.emailNotificationService = emailNotificationService;
    }

    @KafkaListener(topics = "${kafka.topics.booking-confirmed}", groupId = "booking-notification-group", containerFactory = "kafkaListenerContainerFactory")
    public void onBookingConfirmed(@Payload BookingConfirmedEvent event, Acknowledgment ack) {
        String bookingId = event.getBookingId();

        // Re-fetch the full row: the event only carries IDs, not display fields
        // (experienceName, userFullName, bookingDate) needed for the email body.
        Booking booking = bookingRepository.findByBookingId(bookingId).orElse(null);
        if (booking == null) {
            log.error("Booking-confirmed notification skipped: bookingId={} not found (will retry/DLQ)", bookingId);
            throw new IllegalStateException("Booking not found for notification: " + bookingId);
        }

        emailNotificationService.sendBookingConfirmedEmail(booking);
        ack.acknowledge();
    }
}

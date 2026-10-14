package com.forvmom.MomentForeverBooking.consumer;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.events.BookingCancelledEvent;
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
@ConditionalOnProperty(name = "spring.mail.username")
public class BookingCancelledNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingCancelledNotificationConsumer.class);

    private final BookingRepository bookingRepository;
    private final EmailNotificationService emailNotificationService;

    public BookingCancelledNotificationConsumer(BookingRepository bookingRepository,
                                               EmailNotificationService emailNotificationService) {
        this.bookingRepository = bookingRepository;
        this.emailNotificationService = emailNotificationService;
    }

    @KafkaListener(topics = "${kafka.topics.booking-cancelled}", groupId = "booking-notification-group", containerFactory = "kafkaListenerContainerFactory")
    public void onBookingCancelled(@Payload BookingCancelledEvent event, Acknowledgment ack) {
        String bookingId = event.getBookingId();

        // Re-fetch the full row: the event carries IDs, not the display
        // fields (experienceName, userFullName, cancelReason) for the email.
        Booking booking = bookingRepository.findByBookingId(bookingId).orElse(null);
        if (booking == null) {
            log.error("Booking-cancelled notification skipped: bookingId={} not found (will retry/DLQ)", bookingId);
            throw new IllegalStateException("Booking not found for notification: " + bookingId);
        }
        log.info("Sending the cancellation email to the user for the booking {}", booking.getBookingId());
        emailNotificationService.sendBookingCancelledEmail(booking);
        ack.acknowledge();
    }
}

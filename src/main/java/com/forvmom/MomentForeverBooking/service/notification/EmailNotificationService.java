package com.forvmom.MomentForeverBooking.service.notification;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends customer-facing booking emails.
 *
 * <p>
 * PLACEHOLDER: {@code spring.mail.*} in application.yml currently points at
 * placeholder SMTP settings. Real provider credentials (Gmail app password,
 * SES SMTP, SendGrid SMTP relay, etc.) must be supplied via the SMTP_HOST /
 * SMTP_PORT / SMTP_USERNAME / SMTP_PASSWORD / MAIL_FROM_ADDRESS env vars
 * before this can deliver real mail.
 *
 * <p>
 * This bean is only created when {@code spring.mail.username} is set,
 * allowing the application to start without SMTP credentials.
 */
@Service
@ConditionalOnProperty(name = "spring.mail.username")
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendBookingConfirmedEmail(Booking booking) {
        if (booking.getUserEmail() == null || booking.getUserEmail().isBlank()) {
            log.warn("Skipping booking-confirmed email: bookingId={} has no userEmail", booking.getBookingId());
            return;
        }

        String recipientName = booking.getUserFullName() != null ? booking.getUserFullName() : "there";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(booking.getUserEmail());
        message.setSubject("Your booking is confirmed — " + booking.getBookingId());
        message.setText(
                "Hi " + recipientName + ",\n\n" +
                        "Your booking for \"" + booking.getExperienceName() + "\" on " + booking.getBookingDate()
                        + " has been confirmed.\n\n" +
                        "Booking reference: " + booking.getBookingId() + "\n" +
                        "Guests: " + booking.getGuestCount() + "\n\n" +
                        "Thank you for booking with Moment Forever!");

        try {
            mailSender.send(message);
            log.info("Booking-confirmed email sent: bookingId={}, to={}", booking.getBookingId(), booking.getUserEmail());
        } catch (Exception ex) {
            // Let the caller's Kafka error handler decide retry/DLQ policy —
            // this service only logs and rethrows so that policy stays centralized.
            log.error("Failed to send booking-confirmed email: bookingId={}, error={}",
                    booking.getBookingId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}

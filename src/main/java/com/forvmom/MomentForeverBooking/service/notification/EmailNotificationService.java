package com.forvmom.MomentForeverBooking.service.notification;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.events.BookingCancelledEvent;
import com.forvmom.MomentForeverBooking.events.BookingConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import java.nio.charset.StandardCharsets;

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
 * Missing SMTP configuration is handled as an explicit, non-retryable channel
 * skip so the Booking Kafka consumer can continue safely.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${app.notifications.email.enabled:true}")
    private boolean emailEnabled;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public ChannelDeliveryOutcome sendBookingConfirmedEmail(Booking booking) {
        return sendBookingConfirmedEmail(
                booking.getBookingId(),
                booking.getUserEmail(),
                booking.getUserFullName(),
                booking.getExperienceName(),
                String.valueOf(booking.getBookingDate()),
                booking.getGuestCount());
    }

    public ChannelDeliveryOutcome sendBookingConfirmedEmail(BookingConfirmedEvent event) {
        return sendBookingConfirmedEmail(
                event.getBookingId(),
                event.getUserEmail(),
                event.getUserFullName(),
                event.getExperienceName(),
                String.valueOf(event.getBookingDate()),
                event.getGuestCount());
    }

    public ChannelDeliveryOutcome sendBookingCancelledEmail(Booking booking) {
        return sendBookingCancelledEmail(
                booking.getBookingId(),
                booking.getUserEmail(),
                booking.getUserFullName(),
                booking.getExperienceName(),
                String.valueOf(booking.getBookingDate()),
                booking.getCancelReason(),
                booking.getConfirmedAt() != null);
    }

    public ChannelDeliveryOutcome sendBookingCancelledEmail(BookingCancelledEvent event) {
        return sendBookingCancelledEmail(
                event.getBookingId(),
                event.getUserEmail(),
                event.getUserFullName(),
                event.getExperienceName(),
                String.valueOf(event.getBookingDate()),
                event.getCancelReason(),
                event.getConfirmedAt() != null);
    }

    public ChannelDeliveryOutcome sendReceiptEmail(
            String bookingId,
            String receiptNumber,
            String recipient,
            byte[] pdf,
            boolean resend) {
        if (skipDelivery(bookingId, "RECEIPT_DELIVERY", recipient)) {
            return ChannelDeliveryOutcome.SKIPPED;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject((resend ? "Your requested " : "Your ") + "Moment Forever payment receipt");
            helper.setText(
                    "Your immutable Moment Forever payment receipt is attached.\n\n"
                            + "Booking reference: " + bookingId + "\n"
                            + "Receipt number: " + receiptNumber + "\n\n"
                            + "This payment receipt is not a GST tax invoice.");
            helper.addAttachment("receipt-" + receiptNumber + ".pdf", () -> new java.io.ByteArrayInputStream(pdf));
            mailSender.send(message);
            log.info("Receipt email sent: bookingId={}, receiptNumber={}", bookingId, receiptNumber);
            return ChannelDeliveryOutcome.SENT;
        } catch (Exception exception) {
            log.warn("Receipt email delivery failed: bookingId={}, receiptNumber={}, exceptionType={}",
                    bookingId, receiptNumber, exception.getClass().getSimpleName());
            throw new TransientNotificationException("Receipt email delivery failed", exception);
        }
    }

    private ChannelDeliveryOutcome sendBookingConfirmedEmail(
            String bookingId,
            String recipient,
            String recipientName,
            String experienceName,
            String bookingDate,
            Integer guestCount) {
        String body = "Hi " + fallbackName(recipientName) + ",\n\n"
                + "Your booking for \"" + experienceName + "\" on " + bookingDate + " has been confirmed.\n\n"
                + "Booking reference: " + bookingId + "\n"
                + "Guests: " + guestCount + "\n\n"
                + "Thank you for booking with Moment Forever!";
        return sendPlainText(bookingId, "BOOKING_CONFIRMED", recipient,
                "Your booking is confirmed - " + bookingId, body);
    }

    private ChannelDeliveryOutcome sendBookingCancelledEmail(
            String bookingId,
            String recipient,
            String recipientName,
            String experienceName,
            String bookingDate,
            String cancellationReason,
            boolean paymentCaptured) {
        String reasonLine = StringUtils.hasText(cancellationReason) ? "Reason: " + cancellationReason + "\n" : "";
        String refundLine = paymentCaptured
                ? "\nOur team will process your refund manually and notify you separately.\n"
                : "";
        String body = "Hi " + fallbackName(recipientName) + ",\n\n"
                + "Your booking for \"" + experienceName + "\" on " + bookingDate + " has been cancelled.\n\n"
                + "Booking reference: " + bookingId + "\n"
                + reasonLine
                + refundLine
                + "\nWe hope to host you another time.";
        return sendPlainText(bookingId, "BOOKING_CANCELLED", recipient,
                "Your booking is cancelled - " + bookingId, body);
    }

    private ChannelDeliveryOutcome sendPlainText(
            String bookingId,
            String eventType,
            String recipient,
            String subject,
            String body) {
        if (skipDelivery(bookingId, eventType, recipient)) {
            return ChannelDeliveryOutcome.SKIPPED;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body);
            mailSender.send(message);
            log.info("Lifecycle email sent: bookingId={}, eventType={}", bookingId, eventType);
            return ChannelDeliveryOutcome.SENT;
        } catch (Exception exception) {
            log.warn("Lifecycle email delivery failed: bookingId={}, eventType={}, exceptionType={}",
                    bookingId, eventType, exception.getClass().getSimpleName());
            throw new TransientNotificationException("Lifecycle email delivery failed", exception);
        }
    }

    private boolean skipDelivery(String bookingId, String eventType, String recipient) {
        if (!emailEnabled) {
            log.debug("Email notification skipped: bookingId={}, eventType={}, reason=channel_disabled",
                    bookingId, eventType);
            return true;
        }
        if (!isValidRecipient(recipient)) {
            log.warn("Email notification skipped: bookingId={}, eventType={}, reason=invalid_recipient",
                    bookingId, eventType);
            return true;
        }
        if (!StringUtils.hasText(fromAddress) || !StringUtils.hasText(smtpUsername)) {
            log.error("Email notification skipped: bookingId={}, eventType={}, reason=channel_not_configured",
                    bookingId, eventType);
            return true;
        }
        return false;
    }

    private boolean isValidRecipient(String recipient) {
        return StringUtils.hasText(recipient)
                && recipient.length() <= 254
                && recipient.contains("@");
    }

    private String fallbackName(String recipientName) {
        return StringUtils.hasText(recipientName) ? recipientName : "there";
    }
}

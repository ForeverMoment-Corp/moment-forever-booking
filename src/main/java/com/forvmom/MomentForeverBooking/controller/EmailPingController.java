package com.forvmom.MomentForeverBooking.controller;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.service.notification.EmailNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Local-only smoke test for SMTP / booking email notifications.
 *
 * <p>NOT loaded in prod — {@code @Profile({"local", "default"})} means it is
 * only active when you run with no profile or {@code -Dspring.profiles.active=local}.
 * It deliberately bypasses Kafka so you can verify Brevo/Gmail SMTP credentials
 * directly from your laptop:
 *
 * <pre>
 *   $env:SMTP_USERNAME="your-brevo-login"
 *   $env:SMTP_PASSWORD="your-brevo-key"
 *   $env:MAIL_FROM_ADDRESS="verified-sender@yourdomain.com"
 *   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
 *
 *   POST http://localhost:8082/booking/admin/email/ping?to=you@example.com
 *   POST http://localhost:8082/booking/admin/email/booking-ping?to=you@example.com
 * </pre>
 *
 * <p>A {@code 502} with {@code 535 5.7.8 Authentication failed} means the SMTP
 * username/password is wrong or revoked — fix the env vars, not the code.
 * This controller never affects {@code /actuator/health} (mail health check is
 * disabled via {@code management.health.mail.enabled=false}).
 */
@RestController
@RequestMapping("/admin/email")
@Profile({"local", "default"})
@Tag(name = "Local Email Ping", description = "DEV ONLY: verify SMTP without going through Kafka")
public class EmailPingController {

    private final JavaMailSender mailSender;
    private final ObjectProvider<EmailNotificationService> emailService;
    private final String fromAddress;

    public EmailPingController(JavaMailSender mailSender,
                               ObjectProvider<EmailNotificationService> emailService,
                               @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.emailService = emailService;
        this.fromAddress = fromAddress;
    }

    @PostMapping("/ping")
    @Operation(summary = "Raw SMTP ping", description = "Sends a plain test email via JavaMailSender. Use this first to validate credentials.")
    public ResponseEntity<Map<String, String>> ping(@RequestParam String to) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject("Moment Forever SMTP ping — " + LocalDateTime.now());
            message.setText("If you received this, SMTP is configured correctly.\n\nFrom: " + fromAddress);
            mailSender.send(message);
            return ResponseEntity.ok(Map.of(
                    "status", "SENT",
                    "to", to,
                    "from", fromAddress));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "status", "FAILED",
                    "to", to,
                    "error", String.valueOf(ex.getMessage())));
        }
    }

    @PostMapping("/booking-ping")
    @Operation(summary = "Booking template ping", description = "Sends a booking-confirmed email through EmailNotificationService with a dummy booking.")
    public ResponseEntity<Map<String, String>> bookingPing(@RequestParam String to) {
        EmailNotificationService service = emailService.getIfAvailable();
        if (service == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "SKIPPED",
                    "error", "EmailNotificationService bean not present — set spring.mail.username (SMTP_USERNAME) and restart"));
        }
        try {
            Booking booking = new Booking();
            booking.setBookingId("PING-" + System.currentTimeMillis());
            booking.setUserEmail(to);
            booking.setUserFullName("Ping Test");
            booking.setExperienceName("SMTP smoke test");
            booking.setBookingDate(LocalDateTime.now().plusDays(1));
            booking.setGuestCount(2);
            service.sendBookingConfirmedEmail(booking);
            return ResponseEntity.ok(Map.of(
                    "status", "SENT",
                    "to", to,
                    "bookingId", booking.getBookingId()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "status", "FAILED",
                    "to", to,
                    "error", String.valueOf(ex.getMessage())));
        }
    }
}

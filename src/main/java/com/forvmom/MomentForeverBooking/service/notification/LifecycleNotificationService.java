package com.forvmom.MomentForeverBooking.service.notification;

import com.forvmom.MomentForeverBooking.events.BookingCancelledEvent;
import com.forvmom.MomentForeverBooking.events.BookingConfirmedEvent;
import org.springframework.stereotype.Service;

@Service
public class LifecycleNotificationService {

    private final EmailNotificationService emailNotificationService;
    private final WhatsAppNotificationService whatsAppNotificationService;

    public LifecycleNotificationService(
            EmailNotificationService emailNotificationService,
            WhatsAppNotificationService whatsAppNotificationService) {
        this.emailNotificationService = emailNotificationService;
        this.whatsAppNotificationService = whatsAppNotificationService;
    }

    public void notifyConfirmed(BookingConfirmedEvent event) {
        TransientNotificationException transientFailure = null;
        try {
            emailNotificationService.sendBookingConfirmedEmail(event);
        } catch (TransientNotificationException exception) {
            transientFailure = exception;
        }
        try {
            whatsAppNotificationService.sendLifecycleMessage(
                    "BOOKING_CONFIRMED",
                    event.getBookingId(),
                    event.getUserEmail(),
                    "Your Moment Forever booking " + event.getBookingId() + " is confirmed.");
        } catch (TransientNotificationException exception) {
            if (transientFailure == null) {
                transientFailure = exception;
            }
        }
        if (transientFailure != null) {
            throw transientFailure;
        }
    }

    public void notifyCancelled(BookingCancelledEvent event) {
        TransientNotificationException transientFailure = null;
        try {
            emailNotificationService.sendBookingCancelledEmail(event);
        } catch (TransientNotificationException exception) {
            transientFailure = exception;
        }
        try {
            whatsAppNotificationService.sendLifecycleMessage(
                    "BOOKING_CANCELLED",
                    event.getBookingId(),
                    event.getUserEmail(),
                    "Your Moment Forever booking " + event.getBookingId() + " is cancelled.");
        } catch (TransientNotificationException exception) {
            if (transientFailure == null) {
                transientFailure = exception;
            }
        }
        if (transientFailure != null) {
            throw transientFailure;
        }
    }
}

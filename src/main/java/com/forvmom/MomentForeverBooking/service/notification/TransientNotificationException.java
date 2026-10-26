package com.forvmom.MomentForeverBooking.service.notification;

public class TransientNotificationException extends RuntimeException {

    public TransientNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

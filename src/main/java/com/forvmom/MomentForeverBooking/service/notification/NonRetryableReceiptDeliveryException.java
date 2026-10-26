package com.forvmom.MomentForeverBooking.service.notification;

public class NonRetryableReceiptDeliveryException extends RuntimeException {

    public NonRetryableReceiptDeliveryException(String message) {
        super(message);
    }

    public NonRetryableReceiptDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

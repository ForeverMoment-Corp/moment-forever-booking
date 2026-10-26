package com.forvmom.MomentForeverBooking.commons;

// [test-trigger] GitOps pipeline smoke-test — 2026-10-02
public final class EventConstants {

    private EventConstants() {
        // prevent instantiation
    }

    // ============OUTBOX STATUS===========================

    public static final String PENDING = "PENDING";
    public static final String FAILED = "FAILED";
    public static final String PROCESSED = "PROCESSED";
    public static final String PROCESSING = "PROCESSING";
    public static final String DEAD = "DEAD";

    // ==========================================================
    // BOOKING EVENTS
    // ==========================================================

    public static final String BOOKING_REQUESTED = "BOOKING_REQUESTED";
    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    public static final String BOOKING_FAILED = "BOOKING_FAILED";
    public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";
    public static final String RECEIPT_GENERATION_REQUESTED = "RECEIPT_GENERATION_REQUESTED";
    public static final String RECEIPT_ELIGIBILITY_CAPTURED = "RECEIPT_ELIGIBILITY_CAPTURED";
    public static final String EXPERIENCE_COMPLETED = "EXPERIENCE_COMPLETED";

    // ==========================================================
    // PAYMENT EVENTS
    // ==========================================================

    public static final String PAYMENT_REQUESTED = "PAYMENT_REQUESTED";
    public static final String PAYMENT_PROCESSED = "PAYMENT_PROCESSED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_REFUNDED = "PAYMENT_REFUNDED";

}
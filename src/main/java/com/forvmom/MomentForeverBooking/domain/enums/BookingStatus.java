package com.forvmom.MomentForeverBooking.domain.enums;

// PENDING → CONFIRMED → COMPLETED
// PENDING → CONFIRMED → CANCELLED
// PENDING → FAILED
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    FAILED,
    CANCELLED
}

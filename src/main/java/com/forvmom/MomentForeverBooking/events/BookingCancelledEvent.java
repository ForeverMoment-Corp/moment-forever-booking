package com.forvmom.MomentForeverBooking.events;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Published to {@code booking-cancelled} when a PENDING or CONFIRMED booking
 * is cancelled (admin or owner-initiated).
 *
 * <p>Consumed by the platform (core) service to release the date-scoped
 * inventory reserved during the booking request transaction — the same
 * release path as {@code booking-failed}. Field-for-field compatible with
 * the platform's {@code com.forvmom.common.dto.events.BookingCancelledEvent};
 * the producer's TYPE_MAPPINGS tags this payload with that class name so the
 * platform deserializes it directly.
 *
 * <p>IMPORTANT: {@code timeSlotMapperId}, {@code bookingDate} and
 * {@code guestCount} identify the inventory row and quantity to release.
 * There is intentionally no payment data: refunds for CONFIRMED cancels are a
 * manual ops process (see docs/cancellation/README.md).
 */
public class BookingCancelledEvent extends BaseEvent implements OutboundEvent {

    private String bookingId;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private Long experienceId;
    private String experienceName;
    private Long timeSlotMapperId;
    private Integer guestCount;
    private LocalDate bookingDate;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private LocalDateTime confirmedAt;

    public BookingCancelledEvent() {
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Long getExperienceId() {
        return experienceId;
    }

    public String getExperienceName() {
        return experienceName;
    }

    public void setExperienceName(String experienceName) {
        this.experienceName = experienceName;
    }

    public void setExperienceId(Long experienceId) {
        this.experienceId = experienceId;
    }

    public Long getTimeSlotMapperId() {
        return timeSlotMapperId;
    }

    public void setTimeSlotMapperId(Long timeSlotMapperId) {
        this.timeSlotMapperId = timeSlotMapperId;
    }

    public Integer getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(Integer guestCount) {
        this.guestCount = guestCount;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}

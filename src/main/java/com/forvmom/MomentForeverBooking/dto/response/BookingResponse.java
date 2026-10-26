package com.forvmom.MomentForeverBooking.dto.response;

import com.forvmom.MomentForeverBooking.domain.enums.BookingStatus;
import com.forvmom.MomentForeverBooking.domain.enums.PricingLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Booking as exposed by the admin booking APIs (grid, single-get, by-user).
 *
 * <p>Why a DTO instead of the {@code Booking} entity:
 * <ol>
 *   <li><b>Lazy-loading safety.</b> The entity's {@code addons} collection is
 *       LAZY, and Jackson serializes <i>after</i> the read-only transaction
 *       closes (open-in-view is false) — serializing the entity threw
 *       {@code LazyInitializationException: no Session}. DTOs are plain data
 *       mapped <i>inside</i> the transaction, so they carry no proxies.</li>
 *   <li><b>No recursion.</b> The entity graph is bidirectional
 *       ({@code Booking ↔ BookingAddon}); serializing it would recurse
 *       infinitely. The DTO projects a tree, not a graph.</li>
 *   <li><b>Contract stability.</b> Persistence renames (columns, relations,
 *       internal fields like {@code version}) no longer leak into — or break
 *       — the API the dashboard consumes.</li>
 * </ol>
 *
 * <p>Field set mirrors the entity one-to-one today, so existing dashboard
 * clients see no shape change.
 */
public class BookingResponse {

    private String bookingId;
    private LocalDateTime bookingDate;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private String username;
    private Long experienceId;
    private String experienceName;
    private String experienceSlug;
    private Long locationId;
    private String locationName;
    private Long timeSlotMapperId;
    private Long timeSlotId;
    private String timeSlotLabel;
    private String startTime;
    private String endTime;
    private Integer guestCount;
    private BookingStatus status;
    private BigDecimal resolvedPricePerPerson;
    private PricingLevel pricingLevel;
    private BigDecimal totalAmount;
    private BigDecimal addonsTotal;
    private BigDecimal grandTotal;
    private String pincode;
    private String failureReason;
    private String cancelReason;
    private LocalDateTime requestedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<BookingAddonResponse> addons;

    public BookingResponse() {
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public LocalDateTime getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDateTime bookingDate) {
        this.bookingDate = bookingDate;
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

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getExperienceId() {
        return experienceId;
    }

    public void setExperienceId(Long experienceId) {
        this.experienceId = experienceId;
    }

    public String getExperienceName() {
        return experienceName;
    }

    public void setExperienceName(String experienceName) {
        this.experienceName = experienceName;
    }

    public String getExperienceSlug() {
        return experienceSlug;
    }

    public void setExperienceSlug(String experienceSlug) {
        this.experienceSlug = experienceSlug;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public Long getTimeSlotMapperId() {
        return timeSlotMapperId;
    }

    public void setTimeSlotMapperId(Long timeSlotMapperId) {
        this.timeSlotMapperId = timeSlotMapperId;
    }

    public Long getTimeSlotId() {
        return timeSlotId;
    }

    public void setTimeSlotId(Long timeSlotId) {
        this.timeSlotId = timeSlotId;
    }

    public String getTimeSlotLabel() {
        return timeSlotLabel;
    }

    public void setTimeSlotLabel(String timeSlotLabel) {
        this.timeSlotLabel = timeSlotLabel;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Integer getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(Integer guestCount) {
        this.guestCount = guestCount;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public BigDecimal getResolvedPricePerPerson() {
        return resolvedPricePerPerson;
    }

    public void setResolvedPricePerPerson(BigDecimal resolvedPricePerPerson) {
        this.resolvedPricePerPerson = resolvedPricePerPerson;
    }

    public PricingLevel getPricingLevel() {
        return pricingLevel;
    }

    public void setPricingLevel(PricingLevel pricingLevel) {
        this.pricingLevel = pricingLevel;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getAddonsTotal() {
        return addonsTotal;
    }

    public void setAddonsTotal(BigDecimal addonsTotal) {
        this.addonsTotal = addonsTotal;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<BookingAddonResponse> getAddons() {
        return addons;
    }

    public void setAddons(List<BookingAddonResponse> addons) {
        this.addons = addons;
    }
}

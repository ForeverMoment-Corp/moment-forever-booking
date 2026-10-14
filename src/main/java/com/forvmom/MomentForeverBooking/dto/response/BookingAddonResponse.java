package com.forvmom.MomentForeverBooking.dto.response;

import java.math.BigDecimal;

/**
 * Add-on line item as exposed by the admin booking APIs.
 *
 * <p>Deliberately carries no back-reference to the booking: the
 * {@code Booking ↔ BookingAddon} JPA bidirectional link would serialize as
 * infinite recursion ({@code booking → addons → booking → ...}). The DTO
 * breaks the cycle by projecting only the add-on's own columns.
 */
public class BookingAddonResponse {

    private Long id;
    private Long addonMapperId;
    private String addonName;
    private BigDecimal effectivePrice;
    private boolean free;

    public BookingAddonResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAddonMapperId() {
        return addonMapperId;
    }

    public void setAddonMapperId(Long addonMapperId) {
        this.addonMapperId = addonMapperId;
    }

    public String getAddonName() {
        return addonName;
    }

    public void setAddonName(String addonName) {
        this.addonName = addonName;
    }

    public BigDecimal getEffectivePrice() {
        return effectivePrice;
    }

    public void setEffectivePrice(BigDecimal effectivePrice) {
        this.effectivePrice = effectivePrice;
    }

    public boolean isFree() {
        return free;
    }

    public void setFree(boolean free) {
        this.free = free;
    }
}

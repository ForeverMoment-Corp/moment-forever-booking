package com.forvmom.MomentForeverBooking.mapper;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.domain.entity.BookingAddon;
import com.forvmom.MomentForeverBooking.domain.enums.BookingStatus;
import com.forvmom.MomentForeverBooking.domain.enums.PricingLevel;
import com.forvmom.MomentForeverBooking.dto.response.BookingAddonResponse;
import com.forvmom.MomentForeverBooking.dto.response.BookingResponse;
import com.forvmom.MomentForeverBooking.events.BookingRequestEvent;
import com.forvmom.MomentForeverBooking.events.InboundEvent;

import java.time.LocalDateTime;
import java.util.List;

public class BookingMapper {

    public static Booking fromBookingRequestEvent(BookingRequestEvent event) {
        Booking booking = new Booking();
        booking.setBookingId(event.getBookingId());
        booking.setUserId(event.getUserId());
        booking.setUserEmail(event.getUserEmail());
        booking.setUserFullName(event.getUserFullName());
        booking.setUsername(event.getUsername());
        booking.setExperienceId(event.getExperienceId());
        booking.setExperienceName(event.getExperienceName());
        booking.setLocationId(event.getLocationId());
        booking.setLocationName(event.getLocationName());
        booking.setTimeSlotMapperId(event.getTimeSlotMapperId());
        booking.setTimeSlotId(event.getTimeSlotId());
        booking.setTimeSlotLabel(event.getTimeSlotLabel());
        booking.setStartTime(event.getStartTime());
        booking.setEndTime(event.getEndTime());
        if (event.getBookingDate() != null) {
            booking.setBookingDate(event.getBookingDate().atStartOfDay());
        }
        booking.setGuestCount(event.getGuestCount());
        booking.setStatus(BookingStatus.PENDING);
        booking.setResolvedPricePerPerson(event.getResolvedPricePerPerson());
        booking.setPricingLevel(PricingLevel.valueOf(event.getPricingLevel()));
        booking.setTotalAmount(event.getTotalAmount());
        booking.setAddonsTotal(event.getAddonsTotal());
        booking.setGrandTotal(event.getGrandTotal());
        booking.setPincode(event.getPincode());
        booking.setRequestedAt(event.getRequestedAt() != null ? event.getRequestedAt() : LocalDateTime.now());

        if (event.getAddons() != null) {
            event.getAddons().forEach(addonSnap -> {
                BookingAddon addon = new BookingAddon();
                addon.setAddonMapperId(addonSnap.getAddonMapperId());
                addon.setAddonName(addonSnap.getAddonName());
                addon.setEffectivePrice(addonSnap.getEffectivePrice());
                addon.setFree(addonSnap.isFree());
                booking.addAddon(addon);
            });
        }
        return booking;
    }

    /**
     * Projects a managed {@code Booking} to its API response DTO.
     *
     * <p>Must be called <i>inside</i> the service transaction: it reads the
     * LAZY {@code addons} collection, which requires an open session.
     */
    public static BookingResponse toResponse(Booking booking) {
        BookingResponse response = new BookingResponse();
        response.setBookingId(booking.getBookingId());
        response.setBookingDate(booking.getBookingDate());
        response.setUserId(booking.getUserId());
        response.setUserEmail(booking.getUserEmail());
        response.setUserFullName(booking.getUserFullName());
        response.setUsername(booking.getUsername());
        response.setExperienceId(booking.getExperienceId());
        response.setExperienceName(booking.getExperienceName());
        response.setExperienceSlug(booking.getExperienceSlug());
        response.setLocationId(booking.getLocationId());
        response.setLocationName(booking.getLocationName());
        response.setTimeSlotMapperId(booking.getTimeSlotMapperId());
        response.setTimeSlotId(booking.getTimeSlotId());
        response.setTimeSlotLabel(booking.getTimeSlotLabel());
        response.setStartTime(booking.getStartTime());
        response.setEndTime(booking.getEndTime());
        response.setGuestCount(booking.getGuestCount());
        response.setStatus(booking.getStatus());
        response.setResolvedPricePerPerson(booking.getResolvedPricePerPerson());
        response.setPricingLevel(booking.getPricingLevel());
        response.setTotalAmount(booking.getTotalAmount());
        response.setAddonsTotal(booking.getAddonsTotal());
        response.setGrandTotal(booking.getGrandTotal());
        response.setPincode(booking.getPincode());
        response.setFailureReason(booking.getFailureReason());
        response.setCancelReason(booking.getCancelReason());
        response.setRequestedAt(booking.getRequestedAt());
        response.setConfirmedAt(booking.getConfirmedAt());
        response.setCompletedAt(booking.getCompletedAt());
        response.setCancelledAt(booking.getCancelledAt());
        response.setCreatedAt(booking.getCreatedAt());
        response.setUpdatedAt(booking.getUpdatedAt());
        if (booking.getAddons() != null) {
            List<BookingAddonResponse> addons = booking.getAddons().stream()
                    .map(BookingMapper::toAddonResponse)
                    .toList();
            response.setAddons(addons);
        }
        return response;
    }

    /** Projects one add-on row; omits the back-reference to the booking (cycle). */
    public static BookingAddonResponse toAddonResponse(BookingAddon addon) {
        BookingAddonResponse response = new BookingAddonResponse();
        response.setId(addon.getId());
        response.setAddonMapperId(addon.getAddonMapperId());
        response.setAddonName(addon.getAddonName());
        response.setEffectivePrice(addon.getEffectivePrice());
        response.setFree(addon.isFree());
        return response;
    }
}
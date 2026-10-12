package com.forvmom.MomentForeverBooking.controller;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.domain.enums.BookingStatus;
import com.forvmom.MomentForeverBooking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/bookings")
@Tag(name = "Admin Booking Management", description = "Admin endpoints for fetching booking history and managing booking states")
public class BookingAdminController {

    private final BookingService bookingService;

    public BookingAdminController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    @Operation(summary = "List/Filter Bookings", description = "Paginated list of all bookings for the admin booking dashboard. "
            + "Optionally filter by one or more statuses and/or a location. Omit a filter to include every value for it.")
    public ResponseEntity<Page<Booking>> getAllBookings(
            @RequestParam(required = false) List<BookingStatus> status,
            @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> bookings = bookingService.getAllBookings(status, locationId, pageRequest);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get Booking by ID", description = "Fetch full booking details by booking reference ID")
    public ResponseEntity<Booking> getBookingById(@PathVariable String bookingId) {
        Booking booking = bookingService.getBookingByBookingId(bookingId);
        return ResponseEntity.ok(booking);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get Bookings for a User", description = "Paginated list of all bookings made by a specific user, newest first")
    public ResponseEntity<Page<Booking>> getBookingsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> bookings = bookingService.getBookingsByUser(userId, pageRequest);
        return ResponseEntity.ok(bookings);
    }

    @PutMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel Booking", description = "Cancel a booking by its reference ID. Returns 409 if already confirmed.")
    public ResponseEntity<?> cancelBooking(@PathVariable String bookingId) {
        bookingService.cancelBooking(bookingId);
        return ResponseEntity.ok(Map.of(
                "bookingId", bookingId,
                "status", "CANCELLED",
                "message", "Booking has been cancelled successfully."));
    }
}
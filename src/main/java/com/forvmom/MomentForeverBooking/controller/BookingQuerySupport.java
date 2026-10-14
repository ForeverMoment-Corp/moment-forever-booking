package com.forvmom.MomentForeverBooking.controller;

import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Shared query-param handling for the booking list endpoints
 * ({@code BookingAdminController}, {@code BookingUserController}).
 *
 * <p>Kept in one place so both controllers reject unknown sort fields and
 * inverted date ranges with the same clear 400 messages (via
 * {@code IllegalArgumentException}, handled globally) instead of letting
 * Spring Data fail with an opaque 500 further down the stack.
 */
final class BookingQuerySupport {

    private BookingQuerySupport() {
        // Static helpers only.
    }

    /**
     * Parses {@code "field,direction"} into a {@link Sort}, defaulting to
     * {@code "<defaultField>,desc"} when absent. The allow-list keeps
     * {@code sort=} from exposing internal entity fields and from failing
     * with an opaque 500 on unknown properties.
     */
    static Sort parseSort(String sort, Set<String> allowedFields, String defaultField) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, defaultField);
        }

        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!allowedFields.contains(field)) {
            throw new IllegalArgumentException(
                    "Invalid sort field '" + field + "'. Allowed values: " + allowedFields);
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            String rawDirection = parts[1].trim();
            try {
                direction = Sort.Direction.fromString(rawDirection);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Invalid sort direction '" + rawDirection + "'. Allowed values: asc, desc");
            }
        }
        return Sort.by(direction, field);
    }

    /** Rejects an inverted date range instead of silently returning zero rows. */
    static void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' (" + from + ") must not be after 'to' (" + to + ")");
        }
    }
}

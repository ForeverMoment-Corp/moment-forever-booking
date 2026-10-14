package com.forvmom.MomentForeverBooking.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Custom fragment for Booking KPI aggregates.
 *
 * <p>Why a fragment instead of {@code @Query}: the dashboard metrics need the
 * same optional {@code from}/{@code to} range as the grid, and the old
 * {@code (:from IS NULL OR ...)} JPQL hits the same PostgreSQL 42P18
 * indeterminate-datatype failure when the range is omitted. Specifications only
 * cover entity list queries, so aggregates go through {@code EntityManager} +
 * Criteria API in {@link BookingMetricsRepositoryImpl} while reusing the exact
 * same {@code WHERE} filters as the grid (single definition in
 * {@code BookingSearch}).
 *
 * <p>Spring Data wires this automatically: {@code BookingRepository} extends
 * this interface, and the class named {@code BookingMetricsRepositoryImpl}
 * provides the implementation (naming-convention lookup, no config needed).
 */
public interface BookingMetricsRepository {

    /**
     * One row per status present in the range: {@code Object[]{BookingStatus, Long}}.
     * Statuses with zero bookings are absent — callers default them to 0.
     */
    List<Object[]> countByStatusInRange(LocalDateTime from, LocalDateTime to);

    /**
     * Average {@code grandTotal} of CONFIRMED bookings in range, or
     * {@code null} when no confirmed booking exists in range.
     */
    BigDecimal averageConfirmedGrandTotalInRange(LocalDateTime from, LocalDateTime to);
}

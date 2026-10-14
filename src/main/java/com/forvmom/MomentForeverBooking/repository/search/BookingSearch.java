package com.forvmom.MomentForeverBooking.repository.search;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.domain.enums.BookingStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LAYER 3 — ENTITY-SPECIFIC RULES for {@link Booking}.
 *
 * <p>This is the <i>only</i> place that decides <b>which filters a Booking
 * search supports and which entity attributes they map to</b>. Layers 1+2
 * (engine + predicates) never mention bookings; every Booking query in the
 * codebase — list grids, KPI aggregates, future endpoints — must express its
 * {@code WHERE} clause through the factories below. That gives one audit point
 * for "what can callers filter on?" and one place to rename a field safely.
 *
 * <p><b>Field-name constants:</b> filters reference <i>entity attribute</i>
 * names ({@code "createdAt"}), not database columns ({@code "created_at"}).
 * If an attribute is renamed, update the constant here and the compiler shows
 * every affected search; a wrong name fails fast at query-build time
 * (Hibernate metamodel lookup), never as silently wrong SQL.
 *
 * <p><b>Adding a new filter</b> (e.g. {@code experienceId}): add a constant,
 * add a {@code Filter.*} line to the relevant factory, done — no engine or
 * predicate changes. <b>Adding a new endpoint</b>: add a factory composing the
 * existing constants/filters (see {@link #confirmedInRange} as a template).
 */
public final class BookingSearch {

    /** Entity attribute names — single source of truth for Booking filters. */
    public static final String STATUS = "status";
    public static final String LOCATION_ID = "locationId";
    public static final String USER_ID = "userId";
    public static final String CREATED_AT = "createdAt";
    public static final String GRAND_TOTAL = "grandTotal";

    private BookingSearch() {
        // Static factories only.
    }

    /**
     * Admin dashboard grid: every parameter optional (null/empty = no filter).
     * {@code from}/{@code to} bound {@code createdAt} — when the booking row
     * was first persisted. Absent values become no-op conjunctions inside the
     * engine, so PostgreSQL never receives an untyped null (the old 42P18 bug).
     */
    public static Specification<Booking> adminGrid(
            List<BookingStatus> statuses,
            Long locationId,
            Long userId,
            LocalDateTime from,
            LocalDateTime to) {
        return GenericSpecification.of(
                Filter.in(STATUS, statuses),
                Filter.eq(LOCATION_ID, locationId),
                Filter.eq(USER_ID, userId),
                Filter.gte(CREATED_AT, from),
                Filter.lte(CREATED_AT, to));
    }

    /**
     * Shared date-range filter for the KPI/metrics queries. Returned as a
     * {@code List<Filter>} (rather than a spec) so aggregate queries built
     * directly on {@code EntityManager} can reuse the exact same WHERE logic —
     * see {@code BookingMetricsRepositoryImpl}. Null {@code from}/{@code to}
     * means "all time".
     */
    public static List<Filter> createdAtRange(LocalDateTime from, LocalDateTime to) {
        return List.of(
                Filter.gte(CREATED_AT, from),
                Filter.lte(CREATED_AT, to));
    }

    /**
     * CONFIRMED bookings in a date range — backs the "average booking value"
     * KPI. Template for future endpoints: compose constants + factories,
     * no engine changes.
     */
    public static List<Filter> confirmedInRange(LocalDateTime from, LocalDateTime to) {
        return List.of(
                Filter.eq(STATUS, BookingStatus.CONFIRMED),
                Filter.gte(CREATED_AT, from),
                Filter.lte(CREATED_AT, to));
    }

    /**
     * Self-scoped user booking list: always constrained to one
     * {@code userId} (from the gateway's {@code X-User-Id} header), with an
     * optional status filter. Unlike {@link #adminGrid}, the user can never
     * omit the ownership clause — it is hard-wired here, not caller-supplied.
     */
    public static Specification<Booking> userBookings(Long userId, List<BookingStatus> statuses) {
        return GenericSpecification.of(
                Filter.eq(USER_ID, userId),
                Filter.in(STATUS, statuses));
    }
}

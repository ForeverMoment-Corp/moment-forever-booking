package com.forvmom.MomentForeverBooking.repository;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.repository.search.BookingSearch;
import com.forvmom.MomentForeverBooking.repository.search.Filter;
import com.forvmom.MomentForeverBooking.repository.search.SearchPredicates;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Criteria-API implementation of {@link BookingMetricsRepository}.
 *
 * <p><b>How an aggregate Criteria query is built</b> (same shape for both methods):
 * <ol>
 *   <li>{@code em.getCriteriaBuilder()} — entry point for all Criteria construction.</li>
 *   <li>{@code cb.createQuery(ResultType.class)} — declares the query's result type.</li>
 *   <li>{@code query.from(Booking.class)} — binds the query to the Booking entity
 *       and returns its {@code Root<Booking>}; this is where "the entity is found":
 *       Hibernate resolves {@code Booking.class} against the runtime metamodel.</li>
 *   <li>{@code query.where(...)} — WHERE clause assembled from the <i>same</i>
 *       {@link Filter} lists the grid uses ({@code BookingSearch}), rendered by
 *       the generic {@link SearchPredicates} engine. Null range ends contribute
 *       no-ops, so omitted dates never produce untyped null binds (42P18-safe).</li>
 *   <li>Projection: {@code multiselect + groupBy} for the status counts,
 *       {@code cb.avg(...)} for the average booking value.</li>
 * </ol>
 *
 * <p>Naming matters: Spring Data discovers this class purely by the
 * {@code BookingMetricsRepository + "Impl"} convention — no annotation or
 * configuration required.
 */
class BookingMetricsRepositoryImpl implements BookingMetricsRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Object[]> countByStatusInRange(LocalDateTime from, LocalDateTime to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Booking> root = query.from(Booking.class);

        // SELECT status, COUNT(*) ... GROUP BY status, reusing the grid's date-range filters.
        query.multiselect(root.get(BookingSearch.STATUS), cb.count(root));
        query.where(SearchPredicates.toPredicates(root, cb, BookingSearch.createdAtRange(from, to))
                .toArray(new Predicate[0]));
        query.groupBy(root.get(BookingSearch.STATUS));

        // Each row: Object[]{ BookingStatus, Long }. Absent statuses have zero rows.
        return em.createQuery(query).getResultList();
    }

    @Override
    public BigDecimal averageConfirmedGrandTotalInRange(LocalDateTime from, LocalDateTime to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Double> query = cb.createQuery(Double.class);
        Root<Booking> root = query.from(Booking.class);

        // SELECT AVG(grandTotal) ... WHERE status = CONFIRMED AND <range>.
        // JPA defines AVG over numbers as Double; convert to the BigDecimal
        // contract the service expects (null when no confirmed row in range).
        query.select(cb.avg(root.get(BookingSearch.GRAND_TOTAL)));
        List<Filter> filters = BookingSearch.confirmedInRange(from, to);
        query.where(SearchPredicates.toPredicates(root, cb, filters)
                .toArray(new Predicate[0]));

        Double average = em.createQuery(query).getSingleResult();
        return average == null ? null : BigDecimal.valueOf(average);
    }
}

package com.forvmom.MomentForeverBooking.repository.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;

/**
 * LAYER 1 — GENERIC ENGINE (execution).
 *
 * <p>A Spring Data {@link Specification} that ANDs an arbitrary list of generic
 * {@link Filter}s. It declares no entity type of its own ({@code <T>}) and
 * mentions no business field — the <i>caller</i> (an entity-specific search
 * such as {@link BookingSearch}) decides which filters exist; this class only
 * knows how to render them into a Criteria {@code WHERE} clause and hand the
 * result to Spring Data for execution.
 *
 * <p><b>Runtime binding — "how does it find the entity?".</b> This class never
 * names an entity. The entity is supplied at execution time by Spring Data:
 * when the service calls {@code bookingRepository.findAll(spec, pageable)},
 * {@code SimpleJpaRepository} invokes
 * {@code spec.toPredicate(root, query, cb)} with a {@code Root<Booking>} it
 * created from the repository's own domain type ({@code BookingRepository
 * extends JpaRepository<Booking, ...>}). So {@code Root<T>} is always the
 * repository's entity, and {@code root.get("status")} resolves against
 * Hibernate's runtime metamodel for that entity. A misspelled field fails here
 * — at query-build time with {@code IllegalArgumentException: Unable to
 * locate Attribute} — not as wrong SQL.
 *
 * <p>Usage: {@code repository.findAll(GenericSpecification.of(filters), pageable)}.
 * Prefer the entity-specific factories (e.g. {@code BookingSearch.adminGrid(...)})
 * over calling this directly, so supported filters stay defined in one place.
 */
public class GenericSpecification<T> implements Specification<T> {

    private final List<Filter> filters;

    private GenericSpecification(List<Filter> filters) {
        this.filters = filters == null ? List.of() : List.copyOf(filters);
    }

    /** Creates a spec from a filter list. Null/empty list matches everything. */
    public static <T> GenericSpecification<T> of(List<Filter> filters) {
        return new GenericSpecification<>(filters);
    }

    /** Varargs convenience: {@code GenericSpecification.of(Filter.eq(...), ...)}. */
    public static <T> GenericSpecification<T> of(Filter... filters) {
        return new GenericSpecification<>(filters == null ? List.of() : Arrays.asList(filters));
    }

    /** Exposes the filters so aggregate queries can reuse them (see metrics impl). */
    public List<Filter> filters() {
        return filters;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> predicates = SearchPredicates.toPredicates(root, cb, filters);
        if (predicates.isEmpty()) {
            return cb.conjunction();
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }
}

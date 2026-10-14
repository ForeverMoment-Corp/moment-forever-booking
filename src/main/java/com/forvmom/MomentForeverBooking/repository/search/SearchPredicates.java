package com.forvmom.MomentForeverBooking.repository.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * LAYER 2 — REUSABLE SEARCH FEATURES.
 *
 * <p>Translates generic {@link Filter}s into JPA Criteria {@link Predicate}s.
 * Every method here is entity-agnostic: it navigates whatever attribute name
 * the filter carries via {@code root.get(field)} and therefore works for
 * <i>any</i> entity (Booking today, anything else tomorrow) without changes.
 *
 * <p><b>Null-safety contract (the 42P18 fix):</b> a filter whose value is
 * absent (null value, empty IN list, blank LIKE text, both BETWEEN ends null)
 * produces {@code cb.conjunction()} — the "always true" predicate — instead of
 * a comparison against null. Absent filters therefore contribute <i>no bind
 * variable at all</i>, so PostgreSQL never faces an untyped null parameter
 * ({@code SQLState 42P18, indeterminate datatype}).
 *
 * <p><b>Typing note:</b> {@code root.get(field)} returns {@code Path<Object>}
 * because the field type is only known at runtime (Java generics erase it).
 * The {@code @SuppressWarnings} casts below are safe in practice: Hibernate
 * validates the attribute name against the entity metamodel when the query is
 * built, and a wrong-typed value fails fast with a clear
 * {@code IllegalArgumentException} — never silently wrong SQL. Keep
 * {@link Operator} comparisons type-consistent at the call site (dates with
 * dates, numbers with numbers).
 */
public final class SearchPredicates {

    private SearchPredicates() {
        // Static helpers only.
    }

    /** Builds one predicate per filter, in order. Never returns null entries. */
    public static List<Predicate> toPredicates(Root<?> root, CriteriaBuilder cb, List<Filter> filters) {
        List<Predicate> predicates = new ArrayList<>();
        if (filters == null) {
            return predicates;
        }
        for (Filter filter : filters) {
            if (filter != null) {
                predicates.add(toPredicate(root, cb, filter));
            }
        }
        return predicates;
    }

    /** Dispatches one {@link Filter} to the matching CriteriaBuilder comparison. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Predicate toPredicate(Root<?> root, CriteriaBuilder cb, Filter filter) {
        String field = filter.field();
        switch (filter.operator()) {
            case EQ:
                // IS NULL would need cb.isNull; "= NULL" never matches in SQL,
                // so a null EQ value means "filter not requested" -> no-op.
                if (filter.value() == null) {
                    return cb.conjunction();
                }
                return cb.equal(root.get(field), filter.value());

            case IN:
                if (filter.values() == null || filter.values().isEmpty()) {
                    return cb.conjunction();
                }
                return root.get(field).in(filter.values());

            case LIKE:
                if (filter.value() == null || filter.value().toString().isBlank()) {
                    return cb.conjunction();
                }
                // Lower both sides for case-insensitive substring matching.
                return cb.like(
                        cb.lower(root.get(field).as(String.class)),
                        "%" + filter.value().toString().toLowerCase() + "%");

            case GTE:
                if (filter.value() == null) {
                    return cb.conjunction();
                }
                return cb.greaterThanOrEqualTo(root.get(field), (Comparable) filter.value());

            case LTE:
                if (filter.value() == null) {
                    return cb.conjunction();
                }
                return cb.lessThanOrEqualTo(root.get(field), (Comparable) filter.value());

            case GT:
                if (filter.value() == null) {
                    return cb.conjunction();
                }
                return cb.greaterThan(root.get(field), (Comparable) filter.value());

            case LT:
                if (filter.value() == null) {
                    return cb.conjunction();
                }
                return cb.lessThan(root.get(field), (Comparable) filter.value());

            case BETWEEN: {
                Comparable from = (Comparable) filter.value();
                Comparable to = (Comparable) filter.secondValue();
                if (from == null && to == null) {
                    return cb.conjunction();
                }
                // Open-ended ranges degrade gracefully to single-sided bounds.
                if (from == null) {
                    return cb.lessThanOrEqualTo(root.get(field), to);
                }
                if (to == null) {
                    return cb.greaterThanOrEqualTo(root.get(field), from);
                }
                return cb.between(root.get(field), from, to);
            }

            case IS_NULL:
                return cb.isNull(root.get(field));

            case IS_NOT_NULL:
                return cb.isNotNull(root.get(field));

            default:
                throw new IllegalArgumentException("Unsupported operator: " + filter.operator());
        }
    }
}

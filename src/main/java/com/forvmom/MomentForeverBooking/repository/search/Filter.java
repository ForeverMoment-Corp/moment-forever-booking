package com.forvmom.MomentForeverBooking.repository.search;

import java.util.Collection;
import java.util.List;

/**
 * LAYER 1 — GENERIC ENGINE (data).
 *
 * <p>One declarative filter: <i>which field</i>, <i>how to compare</i>, and
 * <i>compare against what</i>. A {@code Filter} references an entity attribute
 * only by name ({@code "status"}, {@code "createdAt"}) — it holds no entity
 * type, so the same object can feed any query.
 *
 * <p>{@code value} carries the comparison value ({@code EQ}, {@code GTE},
 * {@code LIKE}, ...); {@code secondValue} carries the upper bound for
 * {@link Operator#BETWEEN}; {@code values} carries the collection for
 * {@link Operator#IN}. Unused slots stay {@code null}.
 *
 * <p>Use the static factories — never the constructor — so call sites read as
 * business language: {@code Filter.gte("createdAt", from)}.
 */
public record Filter(String field, Operator operator, Object value, Object secondValue, List<?> values) {

    /** Equality: {@code field = value}. A {@code null} value means "no filter". */
    public static Filter eq(String field, Object value) {
        return new Filter(field, Operator.EQ, value, null, null);
    }

    /** Set membership: {@code field IN (values)}. Null/empty means "no filter". */
    public static Filter in(String field, Collection<?> values) {
        return new Filter(field, Operator.IN, null, null,
                values == null ? null : List.copyOf(values));
    }

    /** Case-insensitive substring match. Null/blank means "no filter". */
    public static Filter like(String field, String text) {
        return new Filter(field, Operator.LIKE, text, null, null);
    }

    /** Lower bound (inclusive). Null means "no filter". */
    public static Filter gte(String field, Comparable<?> value) {
        return new Filter(field, Operator.GTE, value, null, null);
    }

    /** Upper bound (inclusive). Null means "no filter". */
    public static Filter lte(String field, Comparable<?> value) {
        return new Filter(field, Operator.LTE, value, null, null);
    }

    /** Strict lower bound. Null means "no filter". */
    public static Filter gt(String field, Comparable<?> value) {
        return new Filter(field, Operator.GT, value, null, null);
    }

    /** Strict upper bound. Null means "no filter". */
    public static Filter lt(String field, Comparable<?> value) {
        return new Filter(field, Operator.LT, value, null, null);
    }

    /**
     * Inclusive range. Either end may be {@code null} (open-ended); both null
     * means "no filter". Single-sided ranges degrade to {@code GTE}/{@code LTE}.
     */
    public static Filter between(String field, Comparable<?> from, Comparable<?> to) {
        return new Filter(field, Operator.BETWEEN, from, to, null);
    }

    /** {@code field IS NULL}. */
    public static Filter isNull(String field) {
        return new Filter(field, Operator.IS_NULL, null, null, null);
    }

    /** {@code field IS NOT NULL}. */
    public static Filter isNotNull(String field) {
        return new Filter(field, Operator.IS_NOT_NULL, null, null, null);
    }
}

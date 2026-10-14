package com.forvmom.MomentForeverBooking.repository.search;

/**
 * LAYER 1 — GENERIC ENGINE (vocabulary).
 *
 * <p>The set of comparisons the search engine understands. This enum knows
 * nothing about bookings, users, or any business entity — it is pure query
 * vocabulary. Adding a new comparison type (e.g. {@code STARTS_WITH}) means
 * adding one constant here plus one branch in {@link SearchPredicates}; no
 * entity code changes.
 */
public enum Operator {

    /** Field equals value: {@code field = :value}. */
    EQ,

    /** Field matches any value: {@code field IN (:values)}. */
    IN,

    /** Case-insensitive substring match on text. */
    LIKE,

    /** {@code field >= :value} (dates, numbers). */
    GTE,

    /** {@code field <= :value} (dates, numbers). */
    LTE,

    /** {@code field > :value}. */
    GT,

    /** {@code field < :value}. */
    LT,

    /** Inclusive range: {@code field BETWEEN :from AND :to}. */
    BETWEEN,

    /** {@code field IS NULL}. */
    IS_NULL,

    /** {@code field IS NOT NULL}. */
    IS_NOT_NULL
}

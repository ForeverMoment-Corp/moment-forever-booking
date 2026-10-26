package com.forvmom.MomentForeverBooking.repository;

import com.forvmom.MomentForeverBooking.domain.entity.Booking;
import com.forvmom.MomentForeverBooking.domain.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, String>, JpaSpecificationExecutor<Booking>,
        BookingMetricsRepository {


    Optional<Booking> findByBookingId(String bookingId);

    boolean existsByBookingId(String bookingId);
    Page<Booking> findAllByUserId(Long userId, Pageable pageable);


    Page<Booking> findAllByStatus(BookingStatus status, Pageable pageable);


    // NOTE: filter and KPI queries intentionally live in Criteria-API code,
    // not in @Query methods with "(:param IS NULL OR ...)" guards — binding
    // null parameters makes PostgreSQL throw 42P18 (indeterminate datatype).
    // Grid filtering: repository/search/BookingSearch (entity rules) on top of
    // the generic engine (GenericSpecification + SearchPredicates), executed
    // via JpaSpecificationExecutor.findAll(spec, pageable). KPI aggregates:
    // BookingMetricsRepositoryImpl, reusing the same BookingSearch filters.
    // Date range filters on createdAt (when the booking row was first persisted).


    // The status predicate makes the first committed terminal transition the only winner.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Booking booking
            SET booking.status = com.forvmom.MomentForeverBooking.domain.enums.BookingStatus.CONFIRMED,
                booking.confirmedAt = :confirmedAt,
                booking.updatedAt = :updatedAt
            WHERE booking.bookingId = :bookingId
              AND booking.status = com.forvmom.MomentForeverBooking.domain.enums.BookingStatus.PENDING
            """)
    int transitionPendingBookingToConfirmed(
            @Param("bookingId") String bookingId,
            @Param("confirmedAt") LocalDateTime confirmedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Booking booking
            SET booking.status = com.forvmom.MomentForeverBooking.domain.enums.BookingStatus.COMPLETED,
                booking.completedAt = :completedAt,
                booking.updatedAt = :updatedAt
            WHERE booking.bookingId = :bookingId
              AND booking.status = com.forvmom.MomentForeverBooking.domain.enums.BookingStatus.CONFIRMED
            """)
    int transitionConfirmedBookingToCompleted(
            @Param("bookingId") String bookingId,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Booking booking
            SET booking.status = com.forvmom.MomentForeverBooking.domain.enums.BookingStatus.FAILED,
                booking.failureReason = :failureReason,
                booking.updatedAt = :updatedAt
            WHERE booking.bookingId = :bookingId
              AND booking.status = com.forvmom.MomentForeverBooking.domain.enums.BookingStatus.PENDING
            """)
    int transitionPendingBookingToFailed(
            @Param("bookingId") String bookingId,
            @Param("failureReason") String failureReason,
            @Param("updatedAt") LocalDateTime updatedAt);

    // Cancellation claim: atomic PENDING/CONFIRMED -> CANCELLED. The
    // status-IN predicate is the concurrency guard — concurrent payment
    // confirmation and cancel race, exactly one CAS wins; the loser sees 0
    // rows. FAILED is deliberately excluded (nothing to cancel; its inventory
    // was already released by the booking-failed flow). Refunds for CONFIRMED
    // cancels are a manual ops process (see docs/cancellation/README.md).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Booking booking
            SET booking.status = com.forvmom.MomentForeverBooking.domain.enums.BookingStatus.CANCELLED,
                booking.cancelReason = :cancelReason,
                booking.cancelledAt = :cancelledAt,
                booking.updatedAt = :updatedAt
            WHERE booking.bookingId = :bookingId
              AND booking.status IN :cancellableStatuses
            """)
    int transitionActiveBookingToCancelled(
            @Param("bookingId") String bookingId,
            @Param("cancelReason") String cancelReason,
            @Param("cancelledAt") LocalDateTime cancelledAt,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("cancellableStatuses") List<BookingStatus> cancellableStatuses);
}

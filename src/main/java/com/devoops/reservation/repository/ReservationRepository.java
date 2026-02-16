package com.devoops.reservation.repository;

import com.devoops.reservation.entity.Reservation;
import com.devoops.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByGuestId(UUID guestId);

    List<Reservation> findByHostId(UUID hostId);

    List<Reservation> findByAccommodationId(UUID accommodationId);

    List<Reservation> findByAccommodationIdAndStatus(UUID accommodationId, ReservationStatus status);

    /**
     * Find approved reservations that overlap with the given date range.
     * Used to check if dates are available for a new reservation.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.accommodationId = :accommodationId
            AND r.status = 'APPROVED'
            AND r.startDate < :endDate
            AND r.endDate > :startDate
            """)
    List<Reservation> findOverlappingApproved(
            @Param("accommodationId") UUID accommodationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Find all pending reservations that overlap with the given date range.
     * Used when approving a reservation to reject overlapping pending requests.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.accommodationId = :accommodationId
            AND r.status = 'PENDING'
            AND r.id != :excludeId
            AND r.startDate < :endDate
            AND r.endDate > :startDate
            """)
    List<Reservation> findOverlappingPending(
            @Param("accommodationId") UUID accommodationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") UUID excludeId
    );

    /**
     * Count cancelled reservations for a guest.
     * Used by hosts when reviewing reservation requests.
     */
    long countByGuestIdAndStatus(UUID guestId, ReservationStatus status);
}

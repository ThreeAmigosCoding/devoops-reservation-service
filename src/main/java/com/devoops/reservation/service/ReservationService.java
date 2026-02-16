package com.devoops.reservation.service;

import com.devoops.reservation.config.UserContext;
import com.devoops.reservation.dto.request.CreateReservationRequest;
import com.devoops.reservation.dto.response.ReservationResponse;
import com.devoops.reservation.entity.Reservation;
import com.devoops.reservation.entity.ReservationStatus;
import com.devoops.reservation.exception.ForbiddenException;
import com.devoops.reservation.exception.InvalidReservationException;
import com.devoops.reservation.exception.ReservationNotFoundException;
import com.devoops.reservation.mapper.ReservationMapper;
import com.devoops.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;

    @Transactional
    public ReservationResponse create(CreateReservationRequest request, UserContext userContext) {
        // Validate dates
        validateDates(request.startDate(), request.endDate());

        // TODO: Call Accommodation Service via gRPC to:
        // 1. Validate accommodation exists
        // 2. Get hostId
        // 3. Validate guest count within min/max capacity
        // 4. Validate dates within availability periods
        // 5. Calculate totalPrice from pricing rules
        UUID hostId = UUID.randomUUID(); // Placeholder - get from Accommodation Service
        BigDecimal totalPrice = calculatePlaceholderPrice(request); // Placeholder - get from Accommodation Service

        // Check for overlapping approved reservations
        List<Reservation> overlapping = reservationRepository.findOverlappingApproved(
                request.accommodationId(),
                request.startDate(),
                request.endDate()
        );

        if (!overlapping.isEmpty()) {
            throw new InvalidReservationException(
                    "The selected dates overlap with an existing approved reservation"
            );
        }

        Reservation reservation = reservationMapper.toEntity(request);
        reservation.setGuestId(userContext.userId());
        reservation.setHostId(hostId);
        reservation.setTotalPrice(totalPrice);
        reservation.setStatus(ReservationStatus.PENDING);

        reservation = reservationRepository.saveAndFlush(reservation);
        log.info("Created reservation {} for guest {} at accommodation {}",
                reservation.getId(), userContext.userId(), request.accommodationId());

        // TODO: Publish event to Notification Service via RabbitMQ
        // notifyHost(reservation);

        return reservationMapper.toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getById(UUID id, UserContext userContext) {
        Reservation reservation = findReservationOrThrow(id);
        validateAccessToReservation(reservation, userContext);
        return reservationMapper.toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getByGuestId(UserContext userContext) {
        List<Reservation> reservations = reservationRepository.findByGuestId(userContext.userId());
        return reservationMapper.toResponseList(reservations);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getByHostId(UserContext userContext) {
        List<Reservation> reservations = reservationRepository.findByHostId(userContext.userId());
        return reservationMapper.toResponseList(reservations);
    }

    @Transactional
    public void deleteRequest(UUID id, UserContext userContext) {
        Reservation reservation = findReservationOrThrow(id);

        // Only the guest who created the reservation can delete it
        if (!reservation.getGuestId().equals(userContext.userId())) {
            throw new ForbiddenException("You can only delete your own reservation requests");
        }

        // Can only delete PENDING requests
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new InvalidReservationException(
                    "Only pending reservation requests can be deleted. Current status: " + reservation.getStatus()
            );
        }

        reservation.setDeleted(true);
        reservationRepository.save(reservation);
        log.info("Guest {} deleted reservation request {}", userContext.userId(), id);

        // TODO: Publish event to Notification Service via RabbitMQ
    }

    // === Helper Methods ===

    private Reservation findReservationOrThrow(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found with id: " + id));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (!endDate.isAfter(startDate)) {
            throw new InvalidReservationException("End date must be after start date");
        }
    }

    private void validateAccessToReservation(Reservation reservation, UserContext userContext) {
        boolean isGuest = reservation.getGuestId().equals(userContext.userId());
        boolean isHost = reservation.getHostId().equals(userContext.userId());

        if (!isGuest && !isHost) {
            throw new ForbiddenException("You do not have access to this reservation");
        }
    }

    /**
     * Placeholder price calculation.
     * TODO: Replace with actual pricing calculation from Accommodation Service via gRPC.
     * This calculates price based on number of nights and guest count.
     */
    private BigDecimal calculatePlaceholderPrice(CreateReservationRequest request) {
        long nights = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
        // Placeholder: $100 per night * guest count
        return BigDecimal.valueOf(100)
                .multiply(BigDecimal.valueOf(nights))
                .multiply(BigDecimal.valueOf(request.guestCount()));
    }
}

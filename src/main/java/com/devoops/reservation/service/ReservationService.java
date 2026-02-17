package com.devoops.reservation.service;

import com.devoops.reservation.config.UserContext;
import com.devoops.reservation.dto.request.CreateReservationRequest;
import com.devoops.reservation.dto.response.ReservationResponse;
import com.devoops.reservation.entity.Reservation;
import com.devoops.reservation.entity.ReservationStatus;
import com.devoops.reservation.exception.AccommodationNotFoundException;
import com.devoops.reservation.exception.ForbiddenException;
import com.devoops.reservation.exception.InvalidReservationException;
import com.devoops.reservation.exception.ReservationNotFoundException;
import com.devoops.reservation.grpc.AccommodationGrpcClient;
import com.devoops.reservation.grpc.AccommodationValidationResult;
import com.devoops.reservation.mapper.ReservationMapper;
import com.devoops.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;
    private final AccommodationGrpcClient accommodationGrpcClient;
    private final ReservationEventPublisherService eventPublisher;

    @Transactional
    public ReservationResponse create(CreateReservationRequest request, UserContext userContext) {
        // Validate dates
        validateDates(request.startDate(), request.endDate());

        // Call Accommodation Service via gRPC to validate and calculate price
        AccommodationValidationResult validationResult = accommodationGrpcClient.validateAndCalculatePrice(
                request.accommodationId(),
                request.startDate(),
                request.endDate(),
                request.guestCount()
        );

        if (!validationResult.valid()) {
            if ("ACCOMMODATION_NOT_FOUND".equals(validationResult.errorCode())) {
                throw new AccommodationNotFoundException(validationResult.errorMessage());
            }
            throw new InvalidReservationException(validationResult.errorMessage());
        }

        UUID hostId = validationResult.hostId();

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
        reservation.setTotalPrice(validationResult.totalPrice());

        // Handle auto-approval mode
        if (validationResult.isAutoApproval()) {
            reservation.setStatus(ReservationStatus.APPROVED);
            log.info("Auto-approving reservation for accommodation {} (AUTOMATIC approval mode)",
                    request.accommodationId());
        } else {
            reservation.setStatus(ReservationStatus.PENDING);
        }

        reservation = reservationRepository.saveAndFlush(reservation);
        log.info("Created reservation {} for guest {} at accommodation {}",
                reservation.getId(), userContext.userId(), request.accommodationId());

        eventPublisher.publishReservationCreated(reservation, validationResult.accommodationName());

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
    }

    @Transactional
    public void cancelReservation(UUID id, UserContext userContext) {
        Reservation reservation = findReservationOrThrow(id);

        // Only the guest who created the reservation can cancel it
        if (!reservation.getGuestId().equals(userContext.userId())) {
            throw new ForbiddenException("You can only cancel your own reservations");
        }

        // Can only cancel APPROVED reservations (PENDING uses deleteRequest)
        if (reservation.getStatus() != ReservationStatus.APPROVED) {
            throw new InvalidReservationException(
                    "Only approved reservations can be cancelled. Use delete for pending requests. Current status: " + reservation.getStatus()
            );
        }

        // Must be at least 1 day before startDate
        LocalDate today = LocalDate.now();
        LocalDate cancellationDeadline = reservation.getStartDate().minusDays(1);

        if (!today.isBefore(cancellationDeadline)) {
            throw new InvalidReservationException(
                    "Reservations can only be cancelled at least 1 day before the start date"
            );
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        log.info("Guest {} cancelled reservation {}", userContext.userId(), id);

        // Fetch accommodation name for notification
        AccommodationValidationResult accommodationInfo = accommodationGrpcClient.validateAndCalculatePrice(
                reservation.getAccommodationId(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getGuestCount()
        );
        String accommodationName = accommodationInfo.valid() ? accommodationInfo.accommodationName() : "Unknown Accommodation";

        eventPublisher.publishReservationCancelled(reservation, accommodationName);
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

}

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private AccommodationGrpcClient accommodationGrpcClient;

    @Mock
    private ReservationEventPublisherService eventPublisher;

    @InjectMocks
    private ReservationService reservationService;

    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID ACCOMMODATION_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final UserContext GUEST_CONTEXT = new UserContext(GUEST_ID, "GUEST");
    private static final UserContext HOST_CONTEXT = new UserContext(HOST_ID, "HOST");

    private Reservation createReservation() {
        return Reservation.builder()
                .id(RESERVATION_ID)
                .accommodationId(ACCOMMODATION_ID)
                .guestId(GUEST_ID)
                .hostId(HOST_ID)
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .guestCount(2)
                .totalPrice(new BigDecimal("1000.00"))
                .status(ReservationStatus.PENDING)
                .build();
    }

    private ReservationResponse createResponse() {
        return new ReservationResponse(
                RESERVATION_ID, ACCOMMODATION_ID, GUEST_ID, HOST_ID,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(15),
                2, new BigDecimal("1000.00"), ReservationStatus.PENDING,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private CreateReservationRequest createRequest() {
        return new CreateReservationRequest(
                ACCOMMODATION_ID,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(15),
                2
        );
    }

    @Nested
    @DisplayName("Create")
    class CreateTests {

        @Test
        @DisplayName("With valid request returns reservation response")
        void create_WithValidRequest_ReturnsReservationResponse() {
            var request = createRequest();
            var reservation = createReservation();
            var response = createResponse();
            var validationResult = new AccommodationValidationResult(
                    true, null, null, HOST_ID, new BigDecimal("1000.00"), "PER_UNIT", "MANUAL"
            );

            when(accommodationGrpcClient.validateAndCalculatePrice(any(), any(), any(), anyInt()))
                    .thenReturn(validationResult);
            when(reservationRepository.findOverlappingApproved(any(), any(), any()))
                    .thenReturn(List.of());
            when(reservationMapper.toEntity(request)).thenReturn(reservation);
            when(reservationRepository.saveAndFlush(reservation)).thenReturn(reservation);
            when(reservationMapper.toResponse(reservation)).thenReturn(response);

            ReservationResponse result = reservationService.create(request, GUEST_CONTEXT);

            assertThat(result).isEqualTo(response);
            assertThat(reservation.getGuestId()).isEqualTo(GUEST_ID);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
            verify(reservationRepository).saveAndFlush(reservation);
            verify(eventPublisher).publishReservationCreated(reservation);
        }

        @Test
        @DisplayName("With overlapping approved reservation throws InvalidReservationException")
        void create_WithOverlappingApproved_ThrowsInvalidReservationException() {
            var request = createRequest();
            var existingReservation = createReservation();
            existingReservation.setStatus(ReservationStatus.APPROVED);
            var validationResult = new AccommodationValidationResult(
                    true, null, null, HOST_ID, new BigDecimal("1000.00"), "PER_UNIT", "MANUAL"
            );

            when(accommodationGrpcClient.validateAndCalculatePrice(any(), any(), any(), anyInt()))
                    .thenReturn(validationResult);
            when(reservationRepository.findOverlappingApproved(any(), any(), any()))
                    .thenReturn(List.of(existingReservation));

            assertThatThrownBy(() -> reservationService.create(request, GUEST_CONTEXT))
                    .isInstanceOf(InvalidReservationException.class)
                    .hasMessageContaining("overlap");
        }

        @Test
        @DisplayName("With auto-approval mode auto-approves reservation")
        void create_WithAutoApprovalMode_AutoApprovesReservation() {
            var request = createRequest();
            var reservation = createReservation();
            var response = createResponse();
            var validationResult = new AccommodationValidationResult(
                    true, null, null, HOST_ID, new BigDecimal("1000.00"), "PER_UNIT", "AUTOMATIC"
            );

            when(accommodationGrpcClient.validateAndCalculatePrice(any(), any(), any(), anyInt()))
                    .thenReturn(validationResult);
            when(reservationRepository.findOverlappingApproved(any(), any(), any()))
                    .thenReturn(List.of());
            when(reservationMapper.toEntity(request)).thenReturn(reservation);
            when(reservationRepository.saveAndFlush(reservation)).thenReturn(reservation);
            when(reservationMapper.toResponse(reservation)).thenReturn(response);

            reservationService.create(request, GUEST_CONTEXT);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.APPROVED);
            verify(reservationRepository).saveAndFlush(reservation);
        }

        @Test
        @DisplayName("With accommodation not found throws AccommodationNotFoundException")
        void create_WithAccommodationNotFound_ThrowsAccommodationNotFoundException() {
            var request = createRequest();
            var validationResult = new AccommodationValidationResult(
                    false, "ACCOMMODATION_NOT_FOUND", "Accommodation not found", null, null, null, null
            );

            when(accommodationGrpcClient.validateAndCalculatePrice(any(), any(), any(), anyInt()))
                    .thenReturn(validationResult);

            assertThatThrownBy(() -> reservationService.create(request, GUEST_CONTEXT))
                    .isInstanceOf(AccommodationNotFoundException.class)
                    .hasMessageContaining("Accommodation not found");
        }

        @Test
        @DisplayName("With invalid guest count throws InvalidReservationException")
        void create_WithInvalidGuestCount_ThrowsInvalidReservationException() {
            var request = createRequest();
            var validationResult = new AccommodationValidationResult(
                    false, "GUEST_COUNT_INVALID", "Guest count must be between 1 and 4", null, null, null, null
            );

            when(accommodationGrpcClient.validateAndCalculatePrice(any(), any(), any(), anyInt()))
                    .thenReturn(validationResult);

            assertThatThrownBy(() -> reservationService.create(request, GUEST_CONTEXT))
                    .isInstanceOf(InvalidReservationException.class)
                    .hasMessageContaining("Guest count");
        }

        @Test
        @DisplayName("With end date before start date throws InvalidReservationException")
        void create_WithEndDateBeforeStartDate_ThrowsInvalidReservationException() {
            var request = new CreateReservationRequest(
                    ACCOMMODATION_ID,
                    LocalDate.now().plusDays(15),
                    LocalDate.now().plusDays(10),
                    2
            );

            assertThatThrownBy(() -> reservationService.create(request, GUEST_CONTEXT))
                    .isInstanceOf(InvalidReservationException.class)
                    .hasMessageContaining("End date must be after start date");
        }

        @Test
        @DisplayName("With same start and end date throws InvalidReservationException")
        void create_WithSameStartAndEndDate_ThrowsInvalidReservationException() {
            LocalDate sameDate = LocalDate.now().plusDays(10);
            var request = new CreateReservationRequest(
                    ACCOMMODATION_ID,
                    sameDate,
                    sameDate,
                    2
            );

            assertThatThrownBy(() -> reservationService.create(request, GUEST_CONTEXT))
                    .isInstanceOf(InvalidReservationException.class)
                    .hasMessageContaining("End date must be after start date");
        }
    }

    @Nested
    @DisplayName("GetById")
    class GetByIdTests {

        @Test
        @DisplayName("With existing ID and guest access returns reservation response")
        void getById_WithExistingIdAndGuestAccess_ReturnsReservationResponse() {
            var reservation = createReservation();
            var response = createResponse();

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservationMapper.toResponse(reservation)).thenReturn(response);

            ReservationResponse result = reservationService.getById(RESERVATION_ID, GUEST_CONTEXT);

            assertThat(result).isEqualTo(response);
        }

        @Test
        @DisplayName("With existing ID and host access returns reservation response")
        void getById_WithExistingIdAndHostAccess_ReturnsReservationResponse() {
            var reservation = createReservation();
            var response = createResponse();

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservationMapper.toResponse(reservation)).thenReturn(response);

            ReservationResponse result = reservationService.getById(RESERVATION_ID, HOST_CONTEXT);

            assertThat(result).isEqualTo(response);
        }

        @Test
        @DisplayName("With non-existing ID throws ReservationNotFoundException")
        void getById_WithNonExistingId_ThrowsReservationNotFoundException() {
            UUID id = UUID.randomUUID();
            when(reservationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.getById(id, GUEST_CONTEXT))
                    .isInstanceOf(ReservationNotFoundException.class);
        }

        @Test
        @DisplayName("With unauthorized user throws ForbiddenException")
        void getById_WithUnauthorizedUser_ThrowsForbiddenException() {
            var reservation = createReservation();
            var otherUser = new UserContext(UUID.randomUUID(), "GUEST");

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.getById(RESERVATION_ID, otherUser))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("GetByGuestId")
    class GetByGuestIdTests {

        @Test
        @DisplayName("With existing guest returns reservation list")
        void getByGuestId_WithExistingGuest_ReturnsReservationList() {
            var reservations = List.of(createReservation());
            var responses = List.of(createResponse());

            when(reservationRepository.findByGuestId(GUEST_ID)).thenReturn(reservations);
            when(reservationMapper.toResponseList(reservations)).thenReturn(responses);

            List<ReservationResponse> result = reservationService.getByGuestId(GUEST_CONTEXT);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("With no reservations returns empty list")
        void getByGuestId_WithNoReservations_ReturnsEmptyList() {
            when(reservationRepository.findByGuestId(GUEST_ID)).thenReturn(List.of());
            when(reservationMapper.toResponseList(List.of())).thenReturn(List.of());

            List<ReservationResponse> result = reservationService.getByGuestId(GUEST_CONTEXT);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("GetByHostId")
    class GetByHostIdTests {

        @Test
        @DisplayName("With existing host returns reservation list")
        void getByHostId_WithExistingHost_ReturnsReservationList() {
            var reservations = List.of(createReservation());
            var responses = List.of(createResponse());

            when(reservationRepository.findByHostId(HOST_ID)).thenReturn(reservations);
            when(reservationMapper.toResponseList(reservations)).thenReturn(responses);

            List<ReservationResponse> result = reservationService.getByHostId(HOST_CONTEXT);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("With no reservations returns empty list")
        void getByHostId_WithNoReservations_ReturnsEmptyList() {
            when(reservationRepository.findByHostId(HOST_ID)).thenReturn(List.of());
            when(reservationMapper.toResponseList(List.of())).thenReturn(List.of());

            List<ReservationResponse> result = reservationService.getByHostId(HOST_CONTEXT);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("DeleteRequest")
    class DeleteRequestTests {

        @Test
        @DisplayName("With valid owner and pending status soft-deletes reservation")
        void deleteRequest_WithValidOwnerAndPending_SoftDeletesReservation() {
            var reservation = createReservation();

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

            reservationService.deleteRequest(RESERVATION_ID, GUEST_CONTEXT);

            assertThat(reservation.isDeleted()).isTrue();
            verify(reservationRepository).save(reservation);
        }

        @Test
        @DisplayName("With wrong owner throws ForbiddenException")
        void deleteRequest_WithWrongOwner_ThrowsForbiddenException() {
            var reservation = createReservation();
            var otherUser = new UserContext(UUID.randomUUID(), "GUEST");

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.deleteRequest(RESERVATION_ID, otherUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("only delete your own");
        }

        @Test
        @DisplayName("With non-pending status throws InvalidReservationException")
        void deleteRequest_WithNonPendingStatus_ThrowsInvalidReservationException() {
            var reservation = createReservation();
            reservation.setStatus(ReservationStatus.APPROVED);

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.deleteRequest(RESERVATION_ID, GUEST_CONTEXT))
                    .isInstanceOf(InvalidReservationException.class)
                    .hasMessageContaining("Only pending");
        }

        @Test
        @DisplayName("With non-existing ID throws ReservationNotFoundException")
        void deleteRequest_WithNonExistingId_ThrowsReservationNotFoundException() {
            UUID id = UUID.randomUUID();
            when(reservationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.deleteRequest(id, GUEST_CONTEXT))
                    .isInstanceOf(ReservationNotFoundException.class);
        }

        @Test
        @DisplayName("Host cannot delete guest's reservation")
        void deleteRequest_WithHostTryingToDelete_ThrowsForbiddenException() {
            var reservation = createReservation();

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.deleteRequest(RESERVATION_ID, HOST_CONTEXT))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("CancelReservation")
    class CancelReservationTests {

        @Test
        @DisplayName("With valid approved reservation cancels successfully")
        void cancelReservation_WithValidApproved_CancelsSuccessfully() {
            var reservation = createReservation();
            reservation.setStatus(ReservationStatus.APPROVED);
            reservation.setStartDate(LocalDate.now().plusDays(10));

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

            reservationService.cancelReservation(RESERVATION_ID, GUEST_CONTEXT);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            verify(reservationRepository).save(reservation);
            verify(eventPublisher).publishReservationCancelled(reservation);
        }

        @Test
        @DisplayName("With wrong owner throws ForbiddenException")
        void cancelReservation_WithWrongOwner_ThrowsForbiddenException() {
            var reservation = createReservation();
            reservation.setStatus(ReservationStatus.APPROVED);
            var otherUser = new UserContext(UUID.randomUUID(), "GUEST");

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.cancelReservation(RESERVATION_ID, otherUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("only cancel your own");
        }

        @Test
        @DisplayName("With pending status throws InvalidReservationException")
        void cancelReservation_WithPendingStatus_ThrowsInvalidReservationException() {
            var reservation = createReservation();
            reservation.setStatus(ReservationStatus.PENDING);

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.cancelReservation(RESERVATION_ID, GUEST_CONTEXT))
                    .isInstanceOf(InvalidReservationException.class)
                    .hasMessageContaining("Only approved reservations");
        }

        @Test
        @DisplayName("With less than 1 day before start throws InvalidReservationException")
        void cancelReservation_WithLessThanOneDayBefore_ThrowsInvalidReservationException() {
            var reservation = createReservation();
            reservation.setStatus(ReservationStatus.APPROVED);
            reservation.setStartDate(LocalDate.now());

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.cancelReservation(RESERVATION_ID, GUEST_CONTEXT))
                    .isInstanceOf(InvalidReservationException.class)
                    .hasMessageContaining("at least 1 day before");
        }

        @Test
        @DisplayName("With non-existing ID throws ReservationNotFoundException")
        void cancelReservation_WithNonExistingId_ThrowsReservationNotFoundException() {
            UUID id = UUID.randomUUID();
            when(reservationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.cancelReservation(id, GUEST_CONTEXT))
                    .isInstanceOf(ReservationNotFoundException.class);
        }

        @Test
        @DisplayName("Host cannot cancel guest's reservation")
        void cancelReservation_WithHostTryingToCancel_ThrowsForbiddenException() {
            var reservation = createReservation();
            reservation.setStatus(ReservationStatus.APPROVED);

            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.cancelReservation(RESERVATION_ID, HOST_CONTEXT))
                    .isInstanceOf(ForbiddenException.class);
        }
    }
}

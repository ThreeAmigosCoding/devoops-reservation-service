package com.devoops.reservation.controller;

import com.devoops.reservation.config.RoleAuthorizationInterceptor;
import com.devoops.reservation.config.UserContext;
import com.devoops.reservation.config.UserContextResolver;
import com.devoops.reservation.dto.response.ReservationResponse;
import com.devoops.reservation.dto.response.ReservationWithGuestInfoResponse;
import com.devoops.reservation.entity.ReservationStatus;
import com.devoops.reservation.exception.ForbiddenException;
import com.devoops.reservation.exception.GlobalExceptionHandler;
import com.devoops.reservation.exception.InvalidReservationException;
import com.devoops.reservation.exception.ReservationNotFoundException;
import com.devoops.reservation.service.ReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationController reservationController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID ACCOMMODATION_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reservationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserContextResolver())
                .addInterceptors(new RoleAuthorizationInterceptor())
                .build();
    }

    private ReservationResponse createResponse() {
        return new ReservationResponse(
                RESERVATION_ID, ACCOMMODATION_ID, "Test Accommodation",
                GUEST_ID, "John Doe", HOST_ID, "Jane Host",
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(15),
                2, new BigDecimal("1000.00"), ReservationStatus.PENDING,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private ReservationResponse createApprovedResponse() {
        return new ReservationResponse(
                RESERVATION_ID, ACCOMMODATION_ID, "Test Accommodation",
                GUEST_ID, "John Doe", HOST_ID, "Jane Host",
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(15),
                2, new BigDecimal("1000.00"), ReservationStatus.APPROVED,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private ReservationResponse createRejectedResponse() {
        return new ReservationResponse(
                RESERVATION_ID, ACCOMMODATION_ID, "Test Accommodation",
                GUEST_ID, "John Doe", HOST_ID, "Jane Host",
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(15),
                2, new BigDecimal("1000.00"), ReservationStatus.REJECTED,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private ReservationWithGuestInfoResponse createResponseWithGuestInfo() {
        return ReservationWithGuestInfoResponse.from(createResponse(), 2L);
    }

    private Map<String, Object> validCreateRequest() {
        return Map.of(
                "accommodationId", ACCOMMODATION_ID.toString(),
                "startDate", LocalDate.now().plusDays(10).toString(),
                "endDate", LocalDate.now().plusDays(15).toString(),
                "guestCount", 2
        );
    }

    @Nested
    @DisplayName("POST /api/reservation")
    class CreateEndpoint {

        @Test
        @DisplayName("With valid request returns 201")
        void create_WithValidRequest_Returns201() throws Exception {
            when(reservationService.create(any(), any(UserContext.class)))
                    .thenReturn(createResponse());

            mockMvc.perform(post("/api/reservation")
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("With missing auth headers returns 401")
        void create_WithMissingAuthHeaders_Returns401() throws Exception {
            mockMvc.perform(post("/api/reservation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("With HOST role returns 403")
        void create_WithHostRole_Returns403() throws Exception {
            mockMvc.perform(post("/api/reservation")
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With missing accommodationId returns 400")
        void create_WithMissingAccommodationId_Returns400() throws Exception {
            var request = Map.of(
                    "startDate", LocalDate.now().plusDays(10).toString(),
                    "endDate", LocalDate.now().plusDays(15).toString(),
                    "guestCount", 2
            );

            mockMvc.perform(post("/api/reservation")
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("With invalid guest count returns 400")
        void create_WithInvalidGuestCount_Returns400() throws Exception {
            var request = Map.of(
                    "accommodationId", ACCOMMODATION_ID.toString(),
                    "startDate", LocalDate.now().plusDays(10).toString(),
                    "endDate", LocalDate.now().plusDays(15).toString(),
                    "guestCount", 0
            );

            mockMvc.perform(post("/api/reservation")
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("With overlapping reservation returns 400")
        void create_WithOverlappingReservation_Returns400() throws Exception {
            when(reservationService.create(any(), any(UserContext.class)))
                    .thenThrow(new InvalidReservationException("overlap with an existing approved reservation"));

            mockMvc.perform(post("/api/reservation")
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/reservation/{id}")
    class GetByIdEndpoint {

        @Test
        @DisplayName("With existing ID and guest role returns 200")
        void getById_WithExistingIdAndGuestRole_Returns200() throws Exception {
            when(reservationService.getById(eq(RESERVATION_ID), any(UserContext.class)))
                    .thenReturn(createResponse());

            mockMvc.perform(get("/api/reservation/{id}", RESERVATION_ID)
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()));
        }

        @Test
        @DisplayName("With existing ID and host role returns 200")
        void getById_WithExistingIdAndHostRole_Returns200() throws Exception {
            when(reservationService.getById(eq(RESERVATION_ID), any(UserContext.class)))
                    .thenReturn(createResponse());

            mockMvc.perform(get("/api/reservation/{id}", RESERVATION_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()));
        }

        @Test
        @DisplayName("With non-existing ID returns 404")
        void getById_WithNonExistingId_Returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(reservationService.getById(eq(id), any(UserContext.class)))
                    .thenThrow(new ReservationNotFoundException("Not found"));

            mockMvc.perform(get("/api/reservation/{id}", id)
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("With unauthorized user returns 403")
        void getById_WithUnauthorizedUser_Returns403() throws Exception {
            when(reservationService.getById(eq(RESERVATION_ID), any(UserContext.class)))
                    .thenThrow(new ForbiddenException("Access denied"));

            mockMvc.perform(get("/api/reservation/{id}", RESERVATION_ID)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/reservation/guest")
    class GetByGuestEndpoint {

        @Test
        @DisplayName("Returns 200 with list")
        void getByGuest_Returns200WithList() throws Exception {
            when(reservationService.getByGuestId(any(UserContext.class)))
                    .thenReturn(List.of(createResponse()));

            mockMvc.perform(get("/api/reservation/guest")
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(RESERVATION_ID.toString()));
        }

        @Test
        @DisplayName("With HOST role returns 403")
        void getByGuest_WithHostRole_Returns403() throws Exception {
            mockMvc.perform(get("/api/reservation/guest")
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/reservation/host")
    class GetByHostEndpoint {

        @Test
        @DisplayName("Returns 200 with list including guest cancellation count")
        void getByHost_Returns200WithList() throws Exception {
            when(reservationService.getByHostIdWithGuestInfo(any(UserContext.class)))
                    .thenReturn(List.of(createResponseWithGuestInfo()));

            mockMvc.perform(get("/api/reservation/host")
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(RESERVATION_ID.toString()))
                    .andExpect(jsonPath("$[0].guestCancellationCount").value(2));
        }

        @Test
        @DisplayName("With GUEST role returns 403")
        void getByHost_WithGuestRole_Returns403() throws Exception {
            mockMvc.perform(get("/api/reservation/host")
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/reservation/{id}")
    class DeleteEndpoint {

        @Test
        @DisplayName("With valid request returns 204")
        void delete_WithValidRequest_Returns204() throws Exception {
            doNothing().when(reservationService).deleteRequest(eq(RESERVATION_ID), any(UserContext.class));

            mockMvc.perform(delete("/api/reservation/{id}", RESERVATION_ID)
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("With non-existing ID returns 404")
        void delete_WithNonExistingId_Returns404() throws Exception {
            UUID id = UUID.randomUUID();
            doThrow(new ReservationNotFoundException("Not found"))
                    .when(reservationService).deleteRequest(eq(id), any(UserContext.class));

            mockMvc.perform(delete("/api/reservation/{id}", id)
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("With wrong owner returns 403")
        void delete_WithWrongOwner_Returns403() throws Exception {
            doThrow(new ForbiddenException("Not the owner"))
                    .when(reservationService).deleteRequest(eq(RESERVATION_ID), any(UserContext.class));

            mockMvc.perform(delete("/api/reservation/{id}", RESERVATION_ID)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With non-pending status returns 400")
        void delete_WithNonPendingStatus_Returns400() throws Exception {
            doThrow(new InvalidReservationException("Only pending requests can be deleted"))
                    .when(reservationService).deleteRequest(eq(RESERVATION_ID), any(UserContext.class));

            mockMvc.perform(delete("/api/reservation/{id}", RESERVATION_ID)
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("With HOST role returns 403")
        void delete_WithHostRole_Returns403() throws Exception {
            mockMvc.perform(delete("/api/reservation/{id}", RESERVATION_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/reservation/{id}/cancel")
    class CancelEndpoint {

        @Test
        @DisplayName("With valid request returns 204")
        void cancel_WithValidRequest_Returns204() throws Exception {
            doNothing().when(reservationService).cancelReservation(eq(RESERVATION_ID), any(UserContext.class));

            mockMvc.perform(post("/api/reservation/{id}/cancel", RESERVATION_ID)
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("With non-existing ID returns 404")
        void cancel_WithNonExistingId_Returns404() throws Exception {
            UUID id = UUID.randomUUID();
            doThrow(new ReservationNotFoundException("Not found"))
                    .when(reservationService).cancelReservation(eq(id), any(UserContext.class));

            mockMvc.perform(post("/api/reservation/{id}/cancel", id)
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("With wrong owner returns 403")
        void cancel_WithWrongOwner_Returns403() throws Exception {
            doThrow(new ForbiddenException("Not the owner"))
                    .when(reservationService).cancelReservation(eq(RESERVATION_ID), any(UserContext.class));

            mockMvc.perform(post("/api/reservation/{id}/cancel", RESERVATION_ID)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With non-approved status returns 400")
        void cancel_WithNonApprovedStatus_Returns400() throws Exception {
            doThrow(new InvalidReservationException("Only approved reservations can be cancelled"))
                    .when(reservationService).cancelReservation(eq(RESERVATION_ID), any(UserContext.class));

            mockMvc.perform(post("/api/reservation/{id}/cancel", RESERVATION_ID)
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("With less than 1 day before start returns 400")
        void cancel_WithTooLate_Returns400() throws Exception {
            doThrow(new InvalidReservationException("at least 1 day before"))
                    .when(reservationService).cancelReservation(eq(RESERVATION_ID), any(UserContext.class));

            mockMvc.perform(post("/api/reservation/{id}/cancel", RESERVATION_ID)
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("With HOST role returns 403")
        void cancel_WithHostRole_Returns403() throws Exception {
            mockMvc.perform(post("/api/reservation/{id}/cancel", RESERVATION_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/reservation/{id}/approve")
    class ApproveEndpoint {

        @Test
        @DisplayName("With valid request returns 200")
        void approve_WithValidRequest_Returns200() throws Exception {
            when(reservationService.approveReservation(eq(RESERVATION_ID), any(UserContext.class)))
                    .thenReturn(createApprovedResponse());

            mockMvc.perform(put("/api/reservation/{id}/approve", RESERVATION_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()))
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }

        @Test
        @DisplayName("With GUEST role returns 403")
        void approve_WithGuestRole_Returns403() throws Exception {
            mockMvc.perform(put("/api/reservation/{id}/approve", RESERVATION_ID)
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With non-existing ID returns 404")
        void approve_WithNonExistingId_Returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(reservationService.approveReservation(eq(id), any(UserContext.class)))
                    .thenThrow(new ReservationNotFoundException("Not found"));

            mockMvc.perform(put("/api/reservation/{id}/approve", id)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("With wrong host returns 403")
        void approve_WithWrongHost_Returns403() throws Exception {
            when(reservationService.approveReservation(eq(RESERVATION_ID), any(UserContext.class)))
                    .thenThrow(new ForbiddenException("Not the host"));

            mockMvc.perform(put("/api/reservation/{id}/approve", RESERVATION_ID)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With non-pending status returns 400")
        void approve_WithNonPendingStatus_Returns400() throws Exception {
            when(reservationService.approveReservation(eq(RESERVATION_ID), any(UserContext.class)))
                    .thenThrow(new InvalidReservationException("Only pending reservations can be approved"));

            mockMvc.perform(put("/api/reservation/{id}/approve", RESERVATION_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/reservation/{id}/reject")
    class RejectEndpoint {

        @Test
        @DisplayName("With valid request returns 200")
        void reject_WithValidRequest_Returns200() throws Exception {
            when(reservationService.rejectReservation(eq(RESERVATION_ID), any(UserContext.class)))
                    .thenReturn(createRejectedResponse());

            mockMvc.perform(put("/api/reservation/{id}/reject", RESERVATION_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()))
                    .andExpect(jsonPath("$.status").value("REJECTED"));
        }

        @Test
        @DisplayName("With GUEST role returns 403")
        void reject_WithGuestRole_Returns403() throws Exception {
            mockMvc.perform(put("/api/reservation/{id}/reject", RESERVATION_ID)
                            .header("X-User-Id", GUEST_ID.toString())
                            .header("X-User-Role", "GUEST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With non-existing ID returns 404")
        void reject_WithNonExistingId_Returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(reservationService.rejectReservation(eq(id), any(UserContext.class)))
                    .thenThrow(new ReservationNotFoundException("Not found"));

            mockMvc.perform(put("/api/reservation/{id}/reject", id)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("With wrong host returns 403")
        void reject_WithWrongHost_Returns403() throws Exception {
            when(reservationService.rejectReservation(eq(RESERVATION_ID), any(UserContext.class)))
                    .thenThrow(new ForbiddenException("Not the host"));

            mockMvc.perform(put("/api/reservation/{id}/reject", RESERVATION_ID)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("With non-pending status returns 400")
        void reject_WithNonPendingStatus_Returns400() throws Exception {
            when(reservationService.rejectReservation(eq(RESERVATION_ID), any(UserContext.class)))
                    .thenThrow(new InvalidReservationException("Only pending reservations can be rejected"));

            mockMvc.perform(put("/api/reservation/{id}/reject", RESERVATION_ID)
                            .header("X-User-Id", HOST_ID.toString())
                            .header("X-User-Role", "HOST"))
                    .andExpect(status().isBadRequest());
        }
    }
}

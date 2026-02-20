package com.devoops.reservation.integration;

import com.devoops.reservation.grpc.AccommodationGrpcClient;
import com.devoops.reservation.grpc.AccommodationValidationResult;
import com.devoops.reservation.grpc.UserGrpcClient;
import com.devoops.reservation.grpc.UserSummaryResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReservationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reservation_db_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccommodationGrpcClient accommodationGrpcClient;

    @MockitoBean
    private UserGrpcClient userGrpcClient;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String reservationId;
    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final UUID OTHER_GUEST_ID = UUID.randomUUID();
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID ACCOMMODATION_ID = UUID.randomUUID();

    private static final String BASE_PATH = "/api/reservation";

    @BeforeEach
    void setUpMocks() {
        AccommodationValidationResult validResult = new AccommodationValidationResult(
                true,
                null,
                null,
                HOST_ID,
                new BigDecimal("500.00"),
                "PER_ACCOMMODATION",
                "MANUAL",
                "Test Accommodation"
        );
        when(accommodationGrpcClient.validateAndCalculatePrice(any(UUID.class), any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(validResult);

        UserSummaryResult hostSummary = new UserSummaryResult(
                true,
                HOST_ID,
                "host@example.com",
                "Test",
                "Host",
                "HOST",
                false
        );
        when(userGrpcClient.getUserSummary(any(UUID.class)))
                .thenReturn(hostSummary);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    private Map<String, Object> validCreateRequest() {
        return Map.of(
                "accommodationId", ACCOMMODATION_ID.toString(),
                "startDate", LocalDate.now().plusDays(10).toString(),
                "endDate", LocalDate.now().plusDays(15).toString(),
                "guestCount", 2
        );
    }

    private Map<String, Object> createRequestWithDates(LocalDate start, LocalDate end) {
        return Map.of(
                "accommodationId", ACCOMMODATION_ID.toString(),
                "startDate", start.toString(),
                "endDate", end.toString(),
                "guestCount", 2
        );
    }

    @Test
    @Order(1)
    @DisplayName("Create reservation with valid request returns 201")
    void create_WithValidRequest_Returns201WithResponse() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accommodationId").value(ACCOMMODATION_ID.toString()))
                .andExpect(jsonPath("$.guestId").value(GUEST_ID.toString()))
                .andExpect(jsonPath("$.guestCount").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.totalPrice").isNotEmpty())
                .andReturn();

        reservationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    @Order(2)
    @DisplayName("Create another pending reservation for same dates is allowed")
    void create_WithOverlappingPendingReservation_Returns201() throws Exception {
        // Multiple pending reservations for overlapping dates should be allowed
        mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", OTHER_GUEST_ID.toString())
                        .header("X-User-Role", "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @Order(3)
    @DisplayName("Create reservation with missing accommodationId returns 400")
    void create_WithMissingAccommodationId_Returns400() throws Exception {
        var request = Map.of(
                "startDate", LocalDate.now().plusDays(10).toString(),
                "endDate", LocalDate.now().plusDays(15).toString(),
                "guestCount", 2
        );

        mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("Create reservation with end date before start date returns 400")
    void create_WithEndDateBeforeStartDate_Returns400() throws Exception {
        var request = createRequestWithDates(
                LocalDate.now().plusDays(15),
                LocalDate.now().plusDays(10)
        );

        mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    @DisplayName("Create reservation with invalid guest count returns 400")
    void create_WithInvalidGuestCount_Returns400() throws Exception {
        var request = Map.of(
                "accommodationId", ACCOMMODATION_ID.toString(),
                "startDate", LocalDate.now().plusDays(10).toString(),
                "endDate", LocalDate.now().plusDays(15).toString(),
                "guestCount", 0
        );

        mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    @DisplayName("Create reservation without auth headers returns 401")
    void create_WithoutAuthHeaders_Returns401() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    @DisplayName("Create reservation with HOST role returns 403")
    void create_WithHostRole_Returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    @DisplayName("Get by ID with existing ID and guest role returns 200")
    void getById_WithExistingIdAndGuestRole_Returns200() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/" + reservationId)
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.guestId").value(GUEST_ID.toString()));
    }

    @Test
    @Order(9)
    @DisplayName("Get by ID with non-existing ID returns 404")
    void getById_WithNonExistingId_Returns404() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/" + UUID.randomUUID())
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(10)
    @DisplayName("Get by ID with unauthorized user returns 403")
    void getById_WithUnauthorizedUser_Returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/" + reservationId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    @DisplayName("Get by guest returns list of reservations")
    void getByGuest_ReturnsListOfReservations() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/guest")
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(12)
    @DisplayName("Get by guest with HOST role returns 403")
    void getByGuest_WithHostRole_Returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/guest")
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    @DisplayName("Get by host returns list of reservations")
    void getByHost_ReturnsListOfReservations() throws Exception {
        // Create a reservation where we know the hostId (from placeholder in service)
        // The service sets a random hostId, so we get the host from the reservation
        MvcResult result = mockMvc.perform(get(BASE_PATH + "/" + reservationId)
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isOk())
                .andReturn();

        String hostId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("hostId").asText();

        mockMvc.perform(get(BASE_PATH + "/host")
                        .header("X-User-Id", hostId)
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(14)
    @DisplayName("Get by host with GUEST role returns 403")
    void getByHost_WithGuestRole_Returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/host")
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(15)
    @DisplayName("Delete reservation with different guest returns 403")
    void delete_WithDifferentGuest_Returns403() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/" + reservationId)
                        .header("X-User-Id", OTHER_GUEST_ID.toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(16)
    @DisplayName("Delete reservation with HOST role returns 403")
    void delete_WithHostRole_Returns403() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/" + reservationId)
                        .header("X-User-Id", HOST_ID.toString())
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(17)
    @DisplayName("Delete pending reservation with valid owner returns 204")
    void delete_WithValidOwner_Returns204() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/" + reservationId)
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(18)
    @DisplayName("After delete, get by ID returns 404 (soft-delete filters)")
    void delete_ThenGetById_Returns404() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/" + reservationId)
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(19)
    @DisplayName("Delete with non-existing ID returns 404")
    void delete_WithNonExistingId_Returns404() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/" + UUID.randomUUID())
                        .header("X-User-Id", GUEST_ID.toString())
                        .header("X-User-Role", "GUEST"))
                .andExpect(status().isNotFound());
    }
}

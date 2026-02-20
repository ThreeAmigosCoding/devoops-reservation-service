package com.devoops.reservation.service;

import com.devoops.reservation.dto.message.ReservationResponseMessage;
import com.devoops.reservation.entity.Reservation;
import com.devoops.reservation.entity.ReservationStatus;
import com.devoops.reservation.grpc.UserGrpcClient;
import com.devoops.reservation.grpc.UserSummaryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationEventPublisherServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private UserGrpcClient userGrpcClient;

    @InjectMocks
    private ReservationEventPublisherService eventPublisher;

    @Captor
    private ArgumentCaptor<ReservationResponseMessage> messageCaptor;

    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    private static final String RESPONSE_ROUTING_KEY = "notification.reservation.response";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eventPublisher, "notificationExchange", NOTIFICATION_EXCHANGE);
        ReflectionTestUtils.setField(eventPublisher, "reservationResponseRoutingKey", RESPONSE_ROUTING_KEY);
    }

    private Reservation createReservation() {
        return Reservation.builder()
                .id(RESERVATION_ID)
                .accommodationId(UUID.randomUUID())
                .guestId(GUEST_ID)
                .hostId(HOST_ID)
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(15))
                .guestCount(2)
                .totalPrice(new BigDecimal("1000.00"))
                .status(ReservationStatus.PENDING)
                .build();
    }

    private UserSummaryResult createGuestSummary() {
        return new UserSummaryResult(true, GUEST_ID, "guest@example.com", "John", "Doe", "GUEST", false);
    }

    private UserSummaryResult createHostSummary() {
        return new UserSummaryResult(true, HOST_ID, "host@example.com", "Jane", "Smith", "HOST", false);
    }

    @Nested
    @DisplayName("PublishReservationResponse")
    class PublishReservationResponseTests {

        @Test
        @DisplayName("With approval publishes APPROVED message")
        void publishReservationResponse_WithApproval_PublishesApprovedMessage() {
            var reservation = createReservation();
            var guestSummary = createGuestSummary();
            var hostSummary = createHostSummary();

            when(userGrpcClient.getUserSummary(GUEST_ID)).thenReturn(guestSummary);
            when(userGrpcClient.getUserSummary(HOST_ID)).thenReturn(hostSummary);

            eventPublisher.publishReservationResponse(reservation, "Beach House", true);

            verify(rabbitTemplate).convertAndSend(
                    eq(NOTIFICATION_EXCHANGE),
                    eq(RESPONSE_ROUTING_KEY),
                    messageCaptor.capture()
            );

            ReservationResponseMessage message = messageCaptor.getValue();
            assertThat(message.userId()).isEqualTo(GUEST_ID);
            assertThat(message.userEmail()).isEqualTo("guest@example.com");
            assertThat(message.hostName()).isEqualTo("Jane Smith");
            assertThat(message.accommodationName()).isEqualTo("Beach House");
            assertThat(message.status()).isEqualTo(ReservationResponseMessage.ReservationResponseStatus.APPROVED);
            assertThat(message.checkIn()).isEqualTo(reservation.getStartDate());
            assertThat(message.checkOut()).isEqualTo(reservation.getEndDate());
        }

        @Test
        @DisplayName("With rejection publishes DECLINED message")
        void publishReservationResponse_WithRejection_PublishesDeclinedMessage() {
            var reservation = createReservation();
            var guestSummary = createGuestSummary();
            var hostSummary = createHostSummary();

            when(userGrpcClient.getUserSummary(GUEST_ID)).thenReturn(guestSummary);
            when(userGrpcClient.getUserSummary(HOST_ID)).thenReturn(hostSummary);

            eventPublisher.publishReservationResponse(reservation, "Mountain Cabin", false);

            verify(rabbitTemplate).convertAndSend(
                    eq(NOTIFICATION_EXCHANGE),
                    eq(RESPONSE_ROUTING_KEY),
                    messageCaptor.capture()
            );

            ReservationResponseMessage message = messageCaptor.getValue();
            assertThat(message.status()).isEqualTo(ReservationResponseMessage.ReservationResponseStatus.DECLINED);
        }

        @Test
        @DisplayName("With missing guest skips publishing")
        void publishReservationResponse_WithMissingGuest_SkipsPublishing() {
            var reservation = createReservation();
            var notFoundSummary = new UserSummaryResult(false, null, null, null, null, null, false);

            when(userGrpcClient.getUserSummary(GUEST_ID)).thenReturn(notFoundSummary);

            eventPublisher.publishReservationResponse(reservation, "Beach House", true);

            verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
        }

        @Test
        @DisplayName("With missing host skips publishing")
        void publishReservationResponse_WithMissingHost_SkipsPublishing() {
            var reservation = createReservation();
            var guestSummary = createGuestSummary();
            var notFoundSummary = new UserSummaryResult(false, null, null, null, null, null, false);

            when(userGrpcClient.getUserSummary(GUEST_ID)).thenReturn(guestSummary);
            when(userGrpcClient.getUserSummary(HOST_ID)).thenReturn(notFoundSummary);

            eventPublisher.publishReservationResponse(reservation, "Beach House", true);

            verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
        }
    }
}

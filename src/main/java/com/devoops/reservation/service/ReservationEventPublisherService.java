package com.devoops.reservation.service;

import com.devoops.reservation.dto.message.ReservationCancelledMessage;
import com.devoops.reservation.dto.message.ReservationCreatedMessage;
import com.devoops.reservation.entity.Reservation;
import com.devoops.reservation.grpc.UserGrpcClient;
import com.devoops.reservation.grpc.UserSummaryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationEventPublisherService {

    private final RabbitTemplate rabbitTemplate;
    private final UserGrpcClient userGrpcClient;

    @Value("${rabbitmq.exchange.notification}")
    private String notificationExchange;

    @Value("${rabbitmq.routing-key.reservation-created}")
    private String reservationCreatedRoutingKey;

    @Value("${rabbitmq.routing-key.reservation-cancelled}")
    private String reservationCancelledRoutingKey;

    public void publishReservationCreated(Reservation reservation, String accommodationName) {
        UserSummaryResult hostSummary = userGrpcClient.getUserSummary(reservation.getHostId());
        UserSummaryResult guestSummary = userGrpcClient.getUserSummary(reservation.getGuestId());

        if (!hostSummary.found()) {
            log.warn("Host not found for reservation {}, skipping notification", reservation.getId());
            return;
        }

        if (!guestSummary.found()) {
            log.warn("Guest not found for reservation {}, skipping notification", reservation.getId());
            return;
        }

        ReservationCreatedMessage message = new ReservationCreatedMessage(
                reservation.getHostId(),
                hostSummary.email(),
                guestSummary.getFullName(),
                accommodationName,
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getTotalPrice()
        );

        log.info("Publishing reservation created event: reservationId={}, hostEmail={}, guestName={}",
                reservation.getId(), hostSummary.email(), guestSummary.getFullName());

        rabbitTemplate.convertAndSend(notificationExchange, reservationCreatedRoutingKey, message);
    }

    public void publishReservationCancelled(Reservation reservation, String accommodationName) {
        UserSummaryResult hostSummary = userGrpcClient.getUserSummary(reservation.getHostId());
        UserSummaryResult guestSummary = userGrpcClient.getUserSummary(reservation.getGuestId());

        if (!hostSummary.found()) {
            log.warn("Host not found for reservation {}, skipping notification", reservation.getId());
            return;
        }

        if (!guestSummary.found()) {
            log.warn("Guest not found for reservation {}, skipping notification", reservation.getId());
            return;
        }

        ReservationCancelledMessage message = new ReservationCancelledMessage(
                reservation.getHostId(),
                hostSummary.email(),
                guestSummary.getFullName(),
                accommodationName,
                reservation.getStartDate(),
                reservation.getEndDate(),
                "Guest cancelled the reservation"
        );

        log.info("Publishing reservation cancelled event: reservationId={}, hostEmail={}, guestName={}",
                reservation.getId(), hostSummary.email(), guestSummary.getFullName());

        rabbitTemplate.convertAndSend(notificationExchange, reservationCancelledRoutingKey, message);
    }
}

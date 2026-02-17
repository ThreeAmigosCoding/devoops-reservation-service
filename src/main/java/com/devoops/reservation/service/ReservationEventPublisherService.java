package com.devoops.reservation.service;

import com.devoops.reservation.dto.message.ReservationCancelledMessage;
import com.devoops.reservation.dto.message.ReservationCreatedMessage;
import com.devoops.reservation.entity.Reservation;
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

    @Value("${rabbitmq.exchange.notification}")
    private String notificationExchange;

    @Value("${rabbitmq.routing-key.reservation-created}")
    private String reservationCreatedRoutingKey;

    @Value("${rabbitmq.routing-key.reservation-cancelled}")
    private String reservationCancelledRoutingKey;

    public void publishReservationCreated(Reservation reservation) {
        ReservationCreatedMessage message = new ReservationCreatedMessage(
                reservation.getId(),
                reservation.getAccommodationId(),
                reservation.getGuestId(),
                reservation.getHostId(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getGuestCount(),
                reservation.getTotalPrice(),
                reservation.getStatus().name()
        );

        log.info("Publishing reservation created event: reservationId={}, status={}",
                reservation.getId(), reservation.getStatus());

        rabbitTemplate.convertAndSend(notificationExchange, reservationCreatedRoutingKey, message);
    }

    public void publishReservationCancelled(Reservation reservation) {
        ReservationCancelledMessage message = new ReservationCancelledMessage(
                reservation.getId(),
                reservation.getAccommodationId(),
                reservation.getGuestId(),
                reservation.getHostId(),
                reservation.getStartDate(),
                reservation.getEndDate()
        );

        log.info("Publishing reservation cancelled event: reservationId={}", reservation.getId());

        rabbitTemplate.convertAndSend(notificationExchange, reservationCancelledRoutingKey, message);
    }
}

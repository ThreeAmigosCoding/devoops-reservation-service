package com.devoops.reservation.dto.response;

import com.devoops.reservation.entity.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID accommodationId,
        String accommodationName,
        UUID guestId,
        String guestName,
        UUID hostId,
        String hostName,
        LocalDate startDate,
        LocalDate endDate,
        int guestCount,
        BigDecimal totalPrice,
        ReservationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
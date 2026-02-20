package com.devoops.reservation.dto.response;

import com.devoops.reservation.entity.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationWithGuestInfoResponse(
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
        LocalDateTime updatedAt,
        long guestCancellationCount
) {
    public static ReservationWithGuestInfoResponse from(ReservationResponse response, long cancellationCount) {
        return new ReservationWithGuestInfoResponse(
                response.id(),
                response.accommodationId(),
                response.accommodationName(),
                response.guestId(),
                response.guestName(),
                response.hostId(),
                response.hostName(),
                response.startDate(),
                response.endDate(),
                response.guestCount(),
                response.totalPrice(),
                response.status(),
                response.createdAt(),
                response.updatedAt(),
                cancellationCount
        );
    }
}

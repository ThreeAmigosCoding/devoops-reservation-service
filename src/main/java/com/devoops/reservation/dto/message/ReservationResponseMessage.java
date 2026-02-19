package com.devoops.reservation.dto.message;

import java.time.LocalDate;
import java.util.UUID;

public record ReservationResponseMessage(
        UUID userId,
        String userEmail,
        String hostName,
        String accommodationName,
        ReservationResponseStatus status,
        LocalDate checkIn,
        LocalDate checkOut
) {
    public enum ReservationResponseStatus {
        APPROVED, DECLINED
    }
}

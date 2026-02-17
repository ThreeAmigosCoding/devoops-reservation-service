package com.devoops.reservation.dto.message;

import java.time.LocalDate;
import java.util.UUID;

public record ReservationCancelledMessage(
        UUID userId,
        String userEmail,
        String guestName,
        String accommodationName,
        LocalDate checkIn,
        LocalDate checkOut,
        String reason
) {
}

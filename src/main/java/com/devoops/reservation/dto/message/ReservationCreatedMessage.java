package com.devoops.reservation.dto.message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationCreatedMessage(
        UUID userId,
        String userEmail,
        String guestName,
        String accommodationName,
        LocalDate checkIn,
        LocalDate checkOut,
        BigDecimal totalPrice
) {
}

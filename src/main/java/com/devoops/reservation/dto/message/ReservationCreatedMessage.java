package com.devoops.reservation.dto.message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationCreatedMessage(
        UUID reservationId,
        UUID accommodationId,
        UUID guestId,
        UUID hostId,
        LocalDate startDate,
        LocalDate endDate,
        int guestCount,
        BigDecimal totalPrice,
        String status
) {
}

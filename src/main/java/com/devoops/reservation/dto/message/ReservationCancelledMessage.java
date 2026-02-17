package com.devoops.reservation.dto.message;

import java.time.LocalDate;
import java.util.UUID;

public record ReservationCancelledMessage(
        UUID reservationId,
        UUID accommodationId,
        UUID guestId,
        UUID hostId,
        LocalDate startDate,
        LocalDate endDate
) {
}

package com.devoops.reservation.grpc;

import java.util.UUID;

public record UserSummaryResult(
        boolean found,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String role
) {
    public String getFullName() {
        return firstName + " " + lastName;
    }
}

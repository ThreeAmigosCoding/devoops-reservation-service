package com.devoops.reservation.grpc;

import java.util.UUID;

public record UserSummaryResult(
        boolean found,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean isDeleted
) {
    public String getFullName() {
        String name = firstName + " " + lastName;
        return isDeleted ? name + " (Deleted)" : name;
    }
}

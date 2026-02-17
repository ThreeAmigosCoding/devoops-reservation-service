package com.devoops.reservation.grpc;

import java.math.BigDecimal;
import java.util.UUID;

public record AccommodationValidationResult(
        boolean valid,
        String errorCode,
        String errorMessage,
        UUID hostId,
        BigDecimal totalPrice,
        String pricingMode,
        String approvalMode
) {
    public boolean isAutoApproval() {
        return "AUTOMATIC".equals(approvalMode);
    }
}

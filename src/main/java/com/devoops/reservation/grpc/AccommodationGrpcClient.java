package com.devoops.reservation.grpc;

import com.devoops.reservation.grpc.proto.accommodation.AccommodationInternalServiceGrpc;
import com.devoops.reservation.grpc.proto.accommodation.ReservationValidationRequest;
import com.devoops.reservation.grpc.proto.accommodation.ReservationValidationResponse;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Component
@Slf4j
public class AccommodationGrpcClient {

    @GrpcClient("accommodation-service")
    private AccommodationInternalServiceGrpc.AccommodationInternalServiceBlockingStub accommodationStub;

    public AccommodationValidationResult validateAndCalculatePrice(
            UUID accommodationId,
            LocalDate startDate,
            LocalDate endDate,
            int guestCount) {

        log.debug("Calling accommodation service for validation: accommodationId={}, dates={} to {}, guests={}",
                accommodationId, startDate, endDate, guestCount);

        ReservationValidationRequest request = ReservationValidationRequest.newBuilder()
                .setAccommodationId(accommodationId.toString())
                .setStartDate(startDate.toString())
                .setEndDate(endDate.toString())
                .setGuestCount(guestCount)
                .build();

        ReservationValidationResponse response = accommodationStub.validateAndCalculatePrice(request);

        log.debug("Received validation response: valid={}, errorCode={}", response.getValid(), response.getErrorCode());

        if (!response.getValid()) {
            return new AccommodationValidationResult(
                    false,
                    response.getErrorCode(),
                    response.getErrorMessage(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        return new AccommodationValidationResult(
                true,
                null,
                null,
                UUID.fromString(response.getHostId()),
                new BigDecimal(response.getTotalPrice()),
                response.getPricingMode(),
                response.getApprovalMode(),
                response.getAccommodationName()
        );
    }
}

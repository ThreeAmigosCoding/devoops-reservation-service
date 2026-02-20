package com.devoops.reservation.grpc;

import com.devoops.reservation.entity.Reservation;
import com.devoops.reservation.grpc.proto.*;
import com.devoops.reservation.repository.ReservationRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class ReservationGrpcService extends ReservationInternalServiceGrpc.ReservationInternalServiceImplBase {

    private final ReservationRepository reservationRepository;

    @Override
    public void checkReservationsExist(CheckReservationsExistRequest request,
                                       StreamObserver<CheckReservationsExistResponse> responseObserver) {
        UUID accommodationId = UUID.fromString(request.getAccommodationId());
        LocalDate startDate = LocalDate.parse(request.getStartDate());
        LocalDate endDate = LocalDate.parse(request.getEndDate());

        log.debug("gRPC: Checking reservations for accommodation {} between {} and {}",
                accommodationId, startDate, endDate);

        List<Reservation> overlapping = reservationRepository.findOverlappingApproved(
                accommodationId, startDate, endDate);

        CheckReservationsExistResponse response = CheckReservationsExistResponse.newBuilder()
                .setHasReservations(!overlapping.isEmpty())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void checkGuestCanBeDeleted(CheckGuestDeletionRequest request,
                                       StreamObserver<CheckDeletionResponse> responseObserver) {
        UUID guestId = UUID.fromString(request.getGuestId());
        LocalDate today = LocalDate.now();

        log.debug("gRPC: Checking if guest {} can be deleted", guestId);

        long activeCount = reservationRepository.countActiveReservationsForGuest(guestId, today);

        CheckDeletionResponse.Builder responseBuilder = CheckDeletionResponse.newBuilder()
                .setActiveReservationCount((int) activeCount);

        if (activeCount > 0) {
            responseBuilder.setCanBeDeleted(false)
                    .setReason("Guest has " + activeCount + " active reservation(s)");
        } else {
            responseBuilder.setCanBeDeleted(true)
                    .setReason("");
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void checkHostCanBeDeleted(CheckHostDeletionRequest request,
                                      StreamObserver<CheckDeletionResponse> responseObserver) {
        UUID hostId = UUID.fromString(request.getHostId());
        LocalDate today = LocalDate.now();

        log.debug("gRPC: Checking if host {} can be deleted", hostId);

        long activeCount = reservationRepository.countActiveReservationsForHost(hostId, today);

        CheckDeletionResponse.Builder responseBuilder = CheckDeletionResponse.newBuilder()
                .setActiveReservationCount((int) activeCount);

        if (activeCount > 0) {
            responseBuilder.setCanBeDeleted(false)
                    .setReason("Host has " + activeCount + " active reservation(s) on their accommodations");
        } else {
            responseBuilder.setCanBeDeleted(true)
                    .setReason("");
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}

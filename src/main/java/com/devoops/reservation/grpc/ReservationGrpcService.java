package com.devoops.reservation.grpc;

import com.devoops.reservation.entity.Reservation;
import com.devoops.reservation.grpc.proto.CheckReservationsExistRequest;
import com.devoops.reservation.grpc.proto.CheckReservationsExistResponse;
import com.devoops.reservation.grpc.proto.ReservationInternalServiceGrpc;
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
}

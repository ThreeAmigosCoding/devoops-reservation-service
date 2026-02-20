package com.devoops.reservation.mapper;

import com.devoops.reservation.dto.request.CreateReservationRequest;
import com.devoops.reservation.dto.response.ReservationResponse;
import com.devoops.reservation.entity.Reservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "guestId", ignore = true)
    @Mapping(target = "hostId", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    Reservation toEntity(CreateReservationRequest request);

    @Mapping(target = "accommodationName", ignore = true)
    @Mapping(target = "guestName", ignore = true)
    @Mapping(target = "hostName", ignore = true)
    ReservationResponse toResponse(Reservation reservation);

    @Mapping(target = "accommodationName", source = "accommodationName")
    @Mapping(target = "guestName", source = "guestName")
    @Mapping(target = "hostName", source = "hostName")
    ReservationResponse toResponseWithNames(Reservation reservation, String accommodationName, String guestName, String hostName);
}
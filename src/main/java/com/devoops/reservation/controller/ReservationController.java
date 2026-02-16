package com.devoops.reservation.controller;

import com.devoops.reservation.config.RequireRole;
import com.devoops.reservation.config.UserContext;
import com.devoops.reservation.dto.request.CreateReservationRequest;
import com.devoops.reservation.dto.response.ReservationResponse;
import com.devoops.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @RequireRole("GUEST")
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody CreateReservationRequest request,
            UserContext userContext) {
        ReservationResponse response = reservationService.create(request, userContext);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @RequireRole({"GUEST", "HOST"})
    public ResponseEntity<ReservationResponse> getById(
            @PathVariable UUID id,
            UserContext userContext) {
        return ResponseEntity.ok(reservationService.getById(id, userContext));
    }


    @GetMapping("/guest")
    @RequireRole("GUEST")
    public ResponseEntity<List<ReservationResponse>> getByGuest(UserContext userContext) {
        return ResponseEntity.ok(reservationService.getByGuestId(userContext));
    }

    @GetMapping("/host")
    @RequireRole("HOST")
    public ResponseEntity<List<ReservationResponse>> getByHost(UserContext userContext) {
        return ResponseEntity.ok(reservationService.getByHostId(userContext));
    }


    @DeleteMapping("/{id}")
    @RequireRole("GUEST")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            UserContext userContext) {
        reservationService.deleteRequest(id, userContext);
        return ResponseEntity.noContent().build();
    }
}

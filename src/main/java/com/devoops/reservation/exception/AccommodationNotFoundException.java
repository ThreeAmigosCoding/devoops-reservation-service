package com.devoops.reservation.exception;

public class AccommodationNotFoundException extends RuntimeException {

    public AccommodationNotFoundException(String message) {
        super(message);
    }
}

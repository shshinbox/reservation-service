package me.songha.concert.reservation.exception;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(UUID reservationId) {
        super("Reservation not found. reservationId=" + reservationId);
    }
}

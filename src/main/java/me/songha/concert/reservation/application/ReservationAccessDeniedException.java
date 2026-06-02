package me.songha.concert.reservation.application;

public class ReservationAccessDeniedException extends RuntimeException {

    public ReservationAccessDeniedException(String message) {
        super(message);
    }
}

package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.Reservation;

public record ReservationCommandResult(
        Reservation reservation,
        boolean completed,
        String message
) {
    public static ReservationCommandResult completed(Reservation reservation) {
        return new ReservationCommandResult(reservation, true, null);
    }

    public static ReservationCommandResult rejected(Reservation reservation, String message) {
        return new ReservationCommandResult(reservation, false, message);
    }
}

package me.songha.concert.reservation.dto;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeat;

import java.util.List;

public record ReservationOperationResult(
        ReservationResponse reservation,
        boolean completed,
        String message
) {
    public static ReservationOperationResult completed(Reservation reservation, List<ReservationSeat> seats) {
        return completed(ReservationResponse.from(reservation, seats));
    }

    public static ReservationOperationResult completed(ReservationResponse reservation) {
        return new ReservationOperationResult(reservation, true, null);
    }

    public static ReservationOperationResult rejected(Reservation reservation, List<ReservationSeat> seats, String message) {
        return rejected(ReservationResponse.from(reservation, seats), message);
    }

    public static ReservationOperationResult rejected(ReservationResponse reservation, String message) {
        return new ReservationOperationResult(reservation, false, message);
    }
}

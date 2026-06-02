package me.songha.concert.reservation.api;

public record ReservationCommandResponse(
        boolean completed,
        String message,
        ReservationResponse reservation
) {
}

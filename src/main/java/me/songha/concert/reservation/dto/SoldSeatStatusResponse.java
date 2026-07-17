package me.songha.concert.reservation.dto;

public record SoldSeatStatusResponse(
        String scheduleId,
        String seatId,
        boolean sold
) {
}

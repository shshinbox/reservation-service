package me.songha.concert.reservation.api;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        UUID holdId,
        String scheduleId,
        String seatId,
        String userId,
        ReservationStatus status,
        Instant holdExpiresAt,
        Instant confirmedAt,
        Instant cancelledAt,
        Instant expiredAt,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getHoldId(),
                reservation.getScheduleId(),
                reservation.getSeatId(),
                reservation.getUserId(),
                reservation.getStatus(),
                reservation.getHoldExpiresAt(),
                reservation.getConfirmedAt(),
                reservation.getCancelledAt(),
                reservation.getExpiredAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt(),
                reservation.getVersion()
        );
    }
}

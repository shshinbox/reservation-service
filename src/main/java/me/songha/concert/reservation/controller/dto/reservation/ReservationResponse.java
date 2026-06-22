package me.songha.concert.reservation.controller.dto.reservation;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeat;
import me.songha.concert.reservation.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        String confirmationId,
        String scheduleId,
        List<String> seatIds,
        String userId,
        ReservationStatus status,
        Instant paymentExpiresAt,
        Instant confirmedAt,
        Instant cancelledAt,
        Instant expiredAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReservationResponse from(Reservation reservation, List<ReservationSeat> seats) {
        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getConfirmationId(),
                reservation.getScheduleId(),
                seats.stream()
                        .map(ReservationSeat::getSeatId)
                        .toList(),
                reservation.getUserId(),
                reservation.getStatus(),
                reservation.getPaymentExpiresAt(),
                reservation.getConfirmedAt(),
                reservation.getCancelledAt(),
                reservation.getExpiredAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }
}

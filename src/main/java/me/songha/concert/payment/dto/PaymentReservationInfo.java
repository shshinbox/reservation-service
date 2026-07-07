package me.songha.concert.payment.dto;

import java.util.UUID;

public record PaymentReservationInfo(
        UUID reservationId,
        String scheduleId,
        String userId
) {
}

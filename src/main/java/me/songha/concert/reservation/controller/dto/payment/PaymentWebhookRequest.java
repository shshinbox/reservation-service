package me.songha.concert.reservation.controller.dto.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentWebhookRequest(
        String eventId,
        String paymentId,
        UUID reservationId,
        String status,
        Instant occurredAt
) {
}

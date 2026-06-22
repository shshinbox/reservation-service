package me.songha.concert.reservation.controller.dto.payment;

public record PaymentWebhookResponse(
        boolean processed,
        String message
) {
}

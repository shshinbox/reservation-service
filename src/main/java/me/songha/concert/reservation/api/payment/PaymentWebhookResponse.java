package me.songha.concert.reservation.api.payment;

public record PaymentWebhookResponse(
        boolean processed,
        String message
) {
}

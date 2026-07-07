package me.songha.concert.payment.dto;

public record PaymentWebhookResponse(
        boolean processed,
        String message
) {
}

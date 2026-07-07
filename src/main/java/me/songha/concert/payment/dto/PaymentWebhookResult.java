package me.songha.concert.payment.dto;

public record PaymentWebhookResult(
        boolean processed,
        String message
) {
    public static PaymentWebhookResult success() {
        return new PaymentWebhookResult(true, null);
    }

    public static PaymentWebhookResult rejected(String message) {
        return new PaymentWebhookResult(false, message);
    }
}

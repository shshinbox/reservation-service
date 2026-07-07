package me.songha.concert.payment.dto;

import java.util.UUID;

public record PaymentStartResponse(
        UUID paymentId,
        String orderId,
        String pgPaymentKey,
        String redirectUrl
) {
}

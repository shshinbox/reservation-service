package me.songha.concert.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentStartRequest(
        UUID reservationId,
        BigDecimal amount,
        String currency,
        String pgProvider
) {
}

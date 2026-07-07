package me.songha.concert.payment.gateway;

import me.songha.concert.payment.entity.PaymentStatus;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface PaymentGateway {

    PaymentGatewayRequestResult requestPayment(PaymentGatewayRequest request);

    PaymentWebhookVerificationResult verifyWebhook(HttpHeaders headers, String rawBody);

    PaymentGatewayResult getPayment(String pgPaymentKey);

    record PaymentGatewayRequest(
            UUID paymentId,
            String orderId,
            UUID reservationId,
            BigDecimal amount,
            String currency,
            String customerId
    ) {
    }

    record PaymentGatewayRequestResult(
            String orderId,
            String pgPaymentKey,
            String redirectUrl
    ) {
    }

    record PaymentWebhookVerificationResult(
            String pgPaymentKey,
            String orderId
    ) {
    }

    record PaymentGatewayResult(
            String pgPaymentKey,
            PaymentStatus status,
            BigDecimal amount,
            String currency,
            String orderId,
            Instant approvedAt,
            String failureCode,
            String failureMessage
    ) {
        public static PaymentGatewayResult approved(String pgPaymentKey, BigDecimal amount, String currency, String orderId) {
            return new PaymentGatewayResult(
                    pgPaymentKey,
                    PaymentStatus.APPROVED,
                    amount,
                    currency,
                    orderId,
                    Instant.now(),
                    null,
                    null
            );
        }
    }
}

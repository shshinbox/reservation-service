package me.songha.concert.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class MockPaymentGateway implements PaymentGateway {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> orderIdsByPaymentKey = new ConcurrentHashMap<>();
    private final String webhookSecret;

    public MockPaymentGateway(
            @Value("${reservation.payment.webhook-secret:local-payment-webhook-secret}") String webhookSecret
    ) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    public PaymentGatewayRequestResult requestPayment(PaymentGatewayRequest request) {
        orderIdsByPaymentKey.put("mock-payment-key", request.orderId());
        return new PaymentGatewayRequestResult(
                request.orderId(),
                "mock-payment-key",
                "http://localhost:8080/mock-payment"
        );
    }

    @Override
    public PaymentWebhookVerificationResult verifyWebhook(HttpHeaders headers, String rawBody) {
        verifySignature(headers, rawBody);
        String orderId = extractOrderId(rawBody);
        orderIdsByPaymentKey.put("mock-payment-key", orderId);
        return new PaymentWebhookVerificationResult(
                "mock-payment-key",
                orderId
        );
    }

    private void verifySignature(HttpHeaders headers, String rawBody) {
        String actualSignature = headers.getFirst("X-Signature");
        if (actualSignature == null || actualSignature.isBlank()) {
            throw new IllegalArgumentException("Webhook signature must not be blank.");
        }

        String expectedSignature = hmacSha256Hex(rawBody, webhookSecret);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                actualSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("Invalid webhook signature.");
        }
    }

    private String hmacSha256Hex(String rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to verify webhook signature.", e);
        }
    }

    @Override
    public PaymentGatewayResult getPayment(String pgPaymentKey) {
        return PaymentGatewayResult.approved(
                pgPaymentKey,
                new BigDecimal("10000"),
                "KRW",
                orderIdsByPaymentKey.getOrDefault(pgPaymentKey, "mock-order-id")
        );
    }

    private String extractOrderId(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode orderId = root.get("orderId");
            if (orderId != null && !orderId.asText().isBlank()) {
                return orderId.asText();
            }
        } catch (Exception ignored) {
        }
        return "mock-order-id";
    }
}

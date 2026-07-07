package me.songha.concert.payment;

import me.songha.concert.payment.gateway.MockPaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentGatewayTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    private final MockPaymentGateway gateway = new MockPaymentGateway(WEBHOOK_SECRET);

    @Test
    void verifyWebhookAcceptsValidSignature() {
        String rawBody = "{\"orderId\":\"order-1\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Signature", hmacSha256Hex(rawBody, WEBHOOK_SECRET));

        var result = gateway.verifyWebhook(headers, rawBody);

        assertThat(result.orderId()).isEqualTo("order-1");
        assertThat(result.pgPaymentKey()).isEqualTo("mock-payment-key");
    }

    @Test
    void verifyWebhookRejectsInvalidSignature() {
        String rawBody = "{\"orderId\":\"order-1\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Signature", "invalid-signature");

        assertThatThrownBy(() -> gateway.verifyWebhook(headers, rawBody))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid webhook signature.");
    }

    @Test
    void verifyWebhookRejectsMissingSignature() {
        assertThatThrownBy(() -> gateway.verifyWebhook(new HttpHeaders(), "{\"orderId\":\"order-1\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Webhook signature must not be blank.");
    }

    private String hmacSha256Hex(String rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

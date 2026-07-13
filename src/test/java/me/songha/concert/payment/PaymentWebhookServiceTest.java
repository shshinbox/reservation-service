package me.songha.concert.payment;

import me.songha.concert.payment.dto.PaymentWebhookResult;
import me.songha.concert.payment.entity.Payment;
import me.songha.concert.payment.entity.PaymentStatus;
import me.songha.concert.payment.gateway.PaymentGateway;
import me.songha.concert.payment.repository.PaymentRepository;
import me.songha.concert.payment.dto.PaymentReservationConfirmResult;
import me.songha.concert.payment.service.PaymentReservationPort;
import me.songha.concert.payment.service.PaymentWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentWebhookServiceTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentGateway paymentGateway = mock(PaymentGateway.class);
    private final PaymentReservationPort paymentReservationPort = mock(PaymentReservationPort.class);
    private final PaymentWebhookService service = new PaymentWebhookService(
            paymentRepository,
            paymentGateway,
            paymentReservationPort
    );

    @Test
    void confirmPaidApprovesPaymentAndConfirmsReservationAfterGatewayVerification() {
        UUID reservationId = UUID.randomUUID();
        Payment payment = payment(reservationId);
        HttpHeaders headers = new HttpHeaders();
        String rawBody = "{\"orderId\":\"order-1\",\"paymentKey\":\"pg-payment-key-1\"}";
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));
        when(paymentGateway.verifyWebhook(headers, rawBody))
                .thenReturn(new PaymentGateway.PaymentWebhookVerificationResult("pg-payment-key-1", "order-1"));
        when(paymentGateway.getPayment("pg-payment-key-1")).thenReturn(approvedGatewayResult());
        when(paymentReservationPort.confirmPaidReservation(reservationId))
                .thenReturn(PaymentReservationConfirmResult.success());

        PaymentWebhookResult result = service.processWebhook(headers, rawBody);

        assertThat(result.processed()).isTrue();
        assertThat(result.message()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(Instant.parse("2026-05-25T11:50:00Z"));
        verify(paymentReservationPort).confirmPaidReservation(reservationId);
    }

    @Test
    void confirmPaidThrowsWhenGatewayAmountDoesNotMatch() {
        UUID reservationId = UUID.randomUUID();
        Payment payment = payment(reservationId);
        HttpHeaders headers = new HttpHeaders();
        String rawBody = "{\"orderId\":\"order-1\",\"paymentKey\":\"pg-payment-key-1\"}";
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));
        when(paymentGateway.verifyWebhook(headers, rawBody))
                .thenReturn(new PaymentGateway.PaymentWebhookVerificationResult("pg-payment-key-1", "order-1"));
        when(paymentGateway.getPayment("pg-payment-key-1")).thenReturn(new PaymentGateway.PaymentGatewayResult(
                "pg-payment-key-1",
                PaymentStatus.APPROVED,
                BigDecimal.valueOf(20000),
                "KRW",
                "order-1",
                Instant.parse("2026-05-25T11:50:00Z"),
                null,
                null
        ));

        assertThatThrownBy(() -> service.processWebhook(headers, rawBody))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment amount mismatch.");
    }

    @Test
    void confirmPaidRejectsWhenPaymentAlreadyExpired() {
        UUID reservationId = UUID.randomUUID();
        Payment payment = payment(reservationId);
        payment.expire(Instant.parse("2026-05-25T11:49:00Z"));
        HttpHeaders headers = new HttpHeaders();
        String rawBody = "{\"orderId\":\"order-1\",\"paymentKey\":\"pg-payment-key-1\"}";
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));
        when(paymentGateway.verifyWebhook(headers, rawBody))
                .thenReturn(new PaymentGateway.PaymentWebhookVerificationResult("pg-payment-key-1", "order-1"));
        when(paymentGateway.getPayment("pg-payment-key-1")).thenReturn(approvedGatewayResult());

        PaymentWebhookResult result = service.processWebhook(headers, rawBody);

        assertThat(result.processed()).isFalse();
        assertThat(result.message()).isEqualTo("Payment is already EXPIRED.");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(paymentReservationPort, never()).confirmPaidReservation(reservationId);
    }

    @Test
    void confirmPaidDoesNotApprovePaymentWhenReservationRejects() {
        UUID reservationId = UUID.randomUUID();
        Payment payment = payment(reservationId);
        HttpHeaders headers = new HttpHeaders();
        String rawBody = "{\"orderId\":\"order-1\",\"paymentKey\":\"pg-payment-key-1\"}";
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));
        when(paymentGateway.verifyWebhook(headers, rawBody))
                .thenReturn(new PaymentGateway.PaymentWebhookVerificationResult("pg-payment-key-1", "order-1"));
        when(paymentGateway.getPayment("pg-payment-key-1")).thenReturn(approvedGatewayResult());
        when(paymentReservationPort.confirmPaidReservation(reservationId))
                .thenReturn(PaymentReservationConfirmResult.rejected("Reservation payment is expired."));

        PaymentWebhookResult result = service.processWebhook(headers, rawBody);

        assertThat(result.processed()).isFalse();
        assertThat(result.message()).isEqualTo("Reservation payment is expired.");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
    }

    private Payment payment(UUID reservationId) {
        Payment payment = new Payment(reservationId, Instant.parse("2026-05-25T11:45:00Z"));
        payment.updateReadyDetails(
                BigDecimal.valueOf(10000),
                "KRW",
                "TEST_PG",
                Instant.parse("2026-05-25T11:45:30Z")
        );
        payment.markRequested("order-1", "pg-payment-key-1", Instant.parse("2026-05-25T11:46:00Z"));
        return payment;
    }

    private PaymentGateway.PaymentGatewayResult approvedGatewayResult() {
        return new PaymentGateway.PaymentGatewayResult(
                "pg-payment-key-1",
                PaymentStatus.APPROVED,
                BigDecimal.valueOf(10000),
                "KRW",
                "order-1",
                Instant.parse("2026-05-25T11:50:00Z"),
                null,
                null
        );
    }

}

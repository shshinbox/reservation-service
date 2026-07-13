package me.songha.concert.payment.service;

import lombok.RequiredArgsConstructor;
import me.songha.concert.payment.dto.PaymentReservationConfirmResult;
import me.songha.concert.payment.gateway.PaymentGateway;
import me.songha.concert.payment.repository.PaymentRepository;
import me.songha.concert.payment.dto.PaymentWebhookResult;
import me.songha.concert.payment.entity.Payment;
import me.songha.concert.payment.entity.PaymentStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentReservationPort paymentReservationPort;

    @Transactional
    public PaymentWebhookResult processWebhook(HttpHeaders headers, String rawBody) {
        PaymentGateway.PaymentWebhookVerificationResult verificationResult =
                paymentGateway.verifyWebhook(headers, rawBody);
        if (verificationResult == null || isBlank(verificationResult.orderId())) {
            throw new IllegalArgumentException("orderId must not be blank.");
        }
        if (isBlank(verificationResult.pgPaymentKey())) {
            throw new IllegalArgumentException("paymentKey must not be blank.");
        }

        Payment payment = paymentRepository.findByOrderId(verificationResult.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found."));
        if (!Objects.equals(payment.getOrderId(), verificationResult.orderId())) {
            throw new IllegalStateException("Payment webhook order id mismatch.");
        }

        PaymentGateway.PaymentGatewayResult gatewayResult = paymentGateway.getPayment(verificationResult.pgPaymentKey());
        verifyPayment(payment, gatewayResult);

        if (gatewayResult.status() != PaymentStatus.APPROVED) {
            throw new IllegalStateException("Payment is not approved.");
        }

        if (isTerminalWithoutApproval(payment.getStatus())) {
            return PaymentWebhookResult.rejected("Payment is already " + payment.getStatus().name() + ".");
        }

        if (payment.getPgPaymentKey() == null && !isBlank(gatewayResult.pgPaymentKey())) {
            payment.updatePgPaymentKey(gatewayResult.pgPaymentKey(), gatewayResult.approvedAt());
        }
        PaymentReservationConfirmResult reservationResult =
                paymentReservationPort.confirmPaidReservation(payment.getReservationId());
        if (!reservationResult.completed()) {
            return PaymentWebhookResult.rejected(reservationResult.message());
        }
        payment.approve(gatewayResult.approvedAt());
        return PaymentWebhookResult.success();
    }

    private boolean isTerminalWithoutApproval(PaymentStatus status) {
        return status == PaymentStatus.CANCELLED
                || status == PaymentStatus.EXPIRED
                || status == PaymentStatus.FAILED;
    }

    private void verifyPayment(Payment payment, PaymentGateway.PaymentGatewayResult gatewayResult) {
        if (gatewayResult == null) {
            throw new IllegalStateException("Payment gateway result must not be null.");
        }
        if (payment.getPgPaymentKey() != null && !Objects.equals(payment.getPgPaymentKey(), gatewayResult.pgPaymentKey())) {
            throw new IllegalStateException("Payment gateway key mismatch.");
        }
        if (isAmountMismatch(payment.getAmount(), gatewayResult.amount())) {
            throw new IllegalStateException("Payment amount mismatch.");
        }
        if (!Objects.equals(payment.getCurrency(), gatewayResult.currency())) {
            throw new IllegalStateException("Payment currency mismatch.");
        }
        if (!Objects.equals(payment.getOrderId(), gatewayResult.orderId())) {
            throw new IllegalStateException("Payment order id mismatch.");
        }
    }

    private boolean isAmountMismatch(BigDecimal expected, BigDecimal actual) {
        return expected == null || actual == null || expected.compareTo(actual) != 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

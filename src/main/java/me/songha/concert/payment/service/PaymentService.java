package me.songha.concert.payment.service;

import lombok.RequiredArgsConstructor;
import me.songha.concert.payment.dto.PaymentReservationInfo;
import me.songha.concert.payment.gateway.PaymentGateway;
import me.songha.concert.payment.repository.PaymentRepository;
import me.songha.concert.payment.dto.PaymentStartRequest;
import me.songha.concert.payment.dto.PaymentStartResponse;
import me.songha.concert.payment.entity.Payment;
import me.songha.concert.schedule.ScheduleClient;
import me.songha.concert.schedule.ScheduleInfo;
import me.songha.concert.time.AppTimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentReservationPort paymentReservationPort;
    private final ScheduleClient scheduleClient;
    private final AppTimeProvider appTimeProvider;

    @Transactional
    public PaymentStartResponse startPayment(PaymentStartRequest request, String authenticatedUserId) {
        validate(request, authenticatedUserId);
        PaymentReservationInfo reservation = paymentReservationPort.getReservationForPayment(request.reservationId());
        if (!reservation.userId().equals(authenticatedUserId)) {
            throw new IllegalArgumentException("Reservation owner mismatch.");
        }
        Instant now = appTimeProvider.nowInstant();
        validateScheduleReservable(reservation.scheduleId(), now);

        Payment payment = paymentRepository.findByReservationId(reservation.reservationId())
                .orElseThrow(() -> new IllegalStateException("Payment draft not found."));
        payment.updateReadyDetails(request.amount(), request.currency(), request.pgProvider(), now);
        String orderId = createOrderId(request.pgProvider());

        PaymentGateway.PaymentGatewayRequestResult gatewayResult = paymentGateway.requestPayment(
                new PaymentGateway.PaymentGatewayRequest(
                        payment.getPaymentId(),
                        orderId,
                        payment.getReservationId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        authenticatedUserId
                )
        );

        if (!orderId.equals(gatewayResult.orderId())) {
            throw new IllegalStateException("Payment order id mismatch.");
        }

        payment.markRequested(orderId, gatewayResult.pgPaymentKey(), appTimeProvider.nowInstant());
        return new PaymentStartResponse(payment.getPaymentId(), orderId, gatewayResult.pgPaymentKey(), gatewayResult.redirectUrl());
    }

    private void validateScheduleReservable(String scheduleId, Instant now) {
        ScheduleInfo schedule = scheduleClient.getSchedule(scheduleId);
        if (schedule == null || !schedule.isReservableAt(now)) {
            throw new IllegalStateException("Schedule is not reservable.");
        }
    }

    private String createOrderId(String pgProvider) {
        return normalizePgProvider(pgProvider) + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String normalizePgProvider(String pgProvider) {
        String normalized = pgProvider.toUpperCase()
                .replaceAll("[^A-Z0-9_-]", "_");
        if (normalized.length() > 20) {
            return normalized.substring(0, 20);
        }
        return normalized;
    }

    private void validate(PaymentStartRequest request, String authenticatedUserId) {
        if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
            throw new IllegalArgumentException("authenticatedUserId must not be blank.");
        }
        if (request == null) {
            throw new IllegalArgumentException("PaymentStartRequest must not be null.");
        }
        if (request.reservationId() == null) {
            throw new IllegalArgumentException("reservationId must not be null.");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive.");
        }
        if (request.currency() == null || request.currency().isBlank()) {
            throw new IllegalArgumentException("currency must not be blank.");
        }
        if (request.pgProvider() == null || request.pgProvider().isBlank()) {
            throw new IllegalArgumentException("pgProvider must not be blank.");
        }
    }
}

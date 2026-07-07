package me.songha.concert.payment;

import me.songha.concert.payment.dto.PaymentStartRequest;
import me.songha.concert.payment.dto.PaymentStartResponse;
import me.songha.concert.payment.entity.Payment;
import me.songha.concert.payment.entity.PaymentStatus;
import me.songha.concert.payment.gateway.PaymentGateway;
import me.songha.concert.payment.repository.PaymentRepository;
import me.songha.concert.payment.dto.PaymentReservationInfo;
import me.songha.concert.payment.service.PaymentReservationPort;
import me.songha.concert.payment.service.PaymentService;
import me.songha.concert.schedule.ScheduleClient;
import me.songha.concert.schedule.ScheduleInfo;
import me.songha.concert.time.AppTimeProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentGateway paymentGateway = mock(PaymentGateway.class);
    private final PaymentReservationPort paymentReservationPort = mock(PaymentReservationPort.class);
    private final ScheduleClient scheduleClient = mock(ScheduleClient.class);
    private final AppTimeProvider appTimeProvider = mock(AppTimeProvider.class);
    private final PaymentService service = new PaymentService(
            paymentRepository,
            paymentGateway,
            paymentReservationPort,
            scheduleClient,
            appTimeProvider
    );

    @Test
    void startPaymentUpdatesReadyPaymentToRequested() {
        PaymentReservationInfo reservation = reservation();
        Payment payment = readyPayment(reservation.reservationId());
        PaymentStartRequest request = new PaymentStartRequest(
                reservation.reservationId(),
                new BigDecimal("12000"),
                "KRW",
                "MOCK"
        );
        when(appTimeProvider.nowInstant()).thenReturn(Instant.parse("2026-05-25T11:50:00Z"));
        when(paymentReservationPort.getReservationForPayment(reservation.reservationId())).thenReturn(reservation);
        when(scheduleClient.getSchedule("schedule-1")).thenReturn(reservableSchedule());
        when(paymentRepository.findByReservationId(reservation.reservationId())).thenReturn(Optional.of(payment));
        when(paymentGateway.requestPayment(any())).thenAnswer(invocation -> {
            PaymentGateway.PaymentGatewayRequest gatewayRequest = invocation.getArgument(0);
            return new PaymentGateway.PaymentGatewayRequestResult(
                    gatewayRequest.orderId(),
                    "pg-payment-key-1",
                    "http://localhost:8080/mock-payment"
            );
        });

        PaymentStartResponse response = service.startPayment(request, "user-1");

        assertThat(response.paymentId()).isEqualTo(payment.getPaymentId());
        assertThat(response.orderId()).isEqualTo(payment.getOrderId());
        assertThat(response.orderId()).startsWith("MOCK_");
        assertThat(response.pgPaymentKey()).isEqualTo("pg-payment-key-1");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getAmount()).isEqualByComparingTo("12000");
        assertThat(payment.getOrderId()).startsWith("MOCK_");
        assertThat(payment.getPgPaymentKey()).isEqualTo("pg-payment-key-1");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void startPaymentThrowsWhenPaymentDraftDoesNotExist() {
        PaymentReservationInfo reservation = reservation();
        PaymentStartRequest request = new PaymentStartRequest(
                reservation.reservationId(),
                new BigDecimal("12000"),
                "KRW",
                "MOCK"
        );
        when(appTimeProvider.nowInstant()).thenReturn(Instant.parse("2026-05-25T11:50:00Z"));
        when(paymentReservationPort.getReservationForPayment(reservation.reservationId())).thenReturn(reservation);
        when(scheduleClient.getSchedule("schedule-1")).thenReturn(reservableSchedule());
        when(paymentRepository.findByReservationId(reservation.reservationId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startPayment(request, "user-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment draft not found.");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void startPaymentThrowsWhenScheduleIsNotReservable() {
        PaymentReservationInfo reservation = reservation();
        PaymentStartRequest request = new PaymentStartRequest(
                reservation.reservationId(),
                new BigDecimal("12000"),
                "KRW",
                "MOCK"
        );
        when(appTimeProvider.nowInstant()).thenReturn(Instant.parse("2026-05-25T11:50:00Z"));
        when(paymentReservationPort.getReservationForPayment(reservation.reservationId())).thenReturn(reservation);
        when(scheduleClient.getSchedule("schedule-1")).thenReturn(new ScheduleInfo(
                "schedule-1",
                Instant.parse("2026-05-25T10:00:00Z"),
                Instant.parse("2026-05-25T12:00:00Z"),
                Instant.parse("2026-05-25T11:00:00Z")
        ));

        assertThatThrownBy(() -> service.startPayment(request, "user-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Schedule is not reservable.");
        verify(paymentRepository, never()).findByReservationId(any());
    }

    private PaymentReservationInfo reservation() {
        return new PaymentReservationInfo(
                UUID.randomUUID(),
                "schedule-1",
                "user-1"
        );
    }

    private Payment readyPayment(UUID reservationId) {
        return new Payment(reservationId, Instant.parse("2026-05-25T11:45:00Z"));
    }

    private ScheduleInfo reservableSchedule() {
        return new ScheduleInfo(
                "schedule-1",
                Instant.parse("2026-05-26T12:00:00Z"),
                Instant.parse("2026-05-26T14:00:00Z"),
                Instant.parse("2026-05-26T11:00:00Z")
        );
    }
}

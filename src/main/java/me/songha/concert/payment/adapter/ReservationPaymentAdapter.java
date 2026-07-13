package me.songha.concert.payment.adapter;

import lombok.RequiredArgsConstructor;
import me.songha.concert.payment.entity.Payment;
import me.songha.concert.payment.entity.PaymentStatus;
import me.songha.concert.payment.repository.PaymentRepository;
import me.songha.concert.reservation.service.ReservationPaymentPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReservationPaymentAdapter implements ReservationPaymentPort {

    private final PaymentRepository paymentRepository;

    @Override
    public void createReadyPayment(UUID reservationId, Instant now) {
        paymentRepository.save(new Payment(reservationId, now));
    }

    @Override
    public void cancelPayment(UUID reservationId, Instant now) {
        Payment payment = paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new IllegalStateException("Payment not found."));
        payment.cancel(now);
    }

    @Override
    public int expirePayments(Collection<UUID> reservationIds, Instant now) {
        if (reservationIds == null || reservationIds.isEmpty()) {
            return 0;
        }
        return paymentRepository.expireByReservationIds(
                reservationIds,
                List.of(PaymentStatus.READY, PaymentStatus.REQUESTED),
                PaymentStatus.EXPIRED,
                now
        );
    }
}

package me.songha.concert.payment.adapter;

import lombok.RequiredArgsConstructor;
import me.songha.concert.payment.entity.Payment;
import me.songha.concert.payment.repository.PaymentRepository;
import me.songha.concert.reservation.service.ReservationPaymentPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReservationPaymentAdapter implements ReservationPaymentPort {

    private final PaymentRepository paymentRepository;

    @Override
    public void createReadyPayment(UUID reservationId, Instant now) {
        paymentRepository.save(new Payment(reservationId, now));
    }
}

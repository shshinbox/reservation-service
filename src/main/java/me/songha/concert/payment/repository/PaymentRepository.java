package me.songha.concert.payment.repository;

import me.songha.concert.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(UUID paymentId);

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByPgPaymentKey(String pgPaymentKey);

    Optional<Payment> findByReservationId(UUID reservationId);
}

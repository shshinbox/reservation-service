package me.songha.concert.payment.repository;

import me.songha.concert.payment.entity.Payment;
import me.songha.concert.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(UUID paymentId);

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByPgPaymentKey(String pgPaymentKey);

    Optional<Payment> findByReservationId(UUID reservationId);

    @Modifying
    @Query("""
            update Payment p
            set p.status = :toStatus,
                p.expiredAt = :now,
                p.updatedAt = :now
            where p.reservationId in :reservationIds
              and p.status in :fromStatuses
            """)
    int expireByReservationIds(
            @Param("reservationIds") Collection<UUID> reservationIds,
            @Param("fromStatuses") Collection<PaymentStatus> fromStatuses,
            @Param("toStatus") PaymentStatus toStatus,
            @Param("now") Instant now
    );
}

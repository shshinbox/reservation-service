package me.songha.concert.reservation.repository;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByConfirmationId(String confirmationId);

    Optional<Reservation> findByReservationId(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.reservationId = :reservationId")
    Optional<Reservation> findByReservationIdForUpdate(@Param("reservationId") UUID reservationId);

    List<Reservation> findByUserIdOrderByCreatedAtDesc(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Reservation> findTop100ByStatusAndPaymentExpiresAtBeforeOrderByPaymentExpiresAtAsc(
            ReservationStatus status,
            Instant now
    );

    @Modifying
    @Query("""
            update Reservation r
            set r.status = :toStatus,
                r.expiredAt = :now,
                r.updatedAt = :now
            where r.reservationId in :reservationIds
              and r.status = :fromStatus
            """)
    int expireByReservationIds(
            @Param("reservationIds") Collection<UUID> reservationIds,
            @Param("fromStatus") ReservationStatus fromStatus,
            @Param("toStatus") ReservationStatus toStatus,
            @Param("now") Instant now
    );
}

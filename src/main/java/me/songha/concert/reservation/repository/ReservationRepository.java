package me.songha.concert.reservation.repository;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByHoldId(UUID holdId);

    Optional<Reservation> findByReservationId(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.reservationId = :reservationId")
    Optional<Reservation> findByReservationIdForUpdate(@Param("reservationId") UUID reservationId);

    Optional<Reservation> findByHoldId(UUID holdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.holdId = :holdId")
    Optional<Reservation> findByHoldIdForUpdate(@Param("holdId") UUID holdId);

    List<Reservation> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Reservation> findByStatusAndHoldExpiresAtBefore(ReservationStatus status, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Reservation> findTop100ByStatusAndHoldExpiresAtBeforeOrderByHoldExpiresAtAsc(
            ReservationStatus status,
            Instant now
    );
}

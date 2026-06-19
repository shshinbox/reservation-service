package me.songha.concert.reservation.repository;

import me.songha.concert.reservation.domain.SoldSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SoldSeatRepository extends JpaRepository<SoldSeat, Long> {

    Optional<SoldSeat> findByReservationIdAndCancelledAtIsNull(UUID reservationId);

    boolean existsByReservationIdAndCancelledAtIsNull(UUID reservationId);
}

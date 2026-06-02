package me.songha.concert.reservation.repository;

import me.songha.concert.reservation.domain.ReservationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationStatusHistoryRepository extends JpaRepository<ReservationStatusHistory, Long> {
}

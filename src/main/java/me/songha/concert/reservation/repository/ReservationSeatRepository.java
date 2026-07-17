package me.songha.concert.reservation.repository;

import me.songha.concert.reservation.domain.ReservationSeat;
import me.songha.concert.reservation.domain.ReservationSeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    boolean existsByScheduleIdAndSeatIdAndStatusIn(
            String scheduleId,
            String seatId,
            Collection<ReservationSeatStatus> statuses
    );

    List<ReservationSeat> findByReservationIdOrderBySeatIdAsc(UUID reservationId);

    List<ReservationSeat> findByScheduleIdAndStatusOrderBySeatIdAsc(
            String scheduleId,
            ReservationSeatStatus status
    );

    @Modifying
    @Query("""
            update ReservationSeat rs
            set rs.status = :toStatus,
                rs.updatedAt = :now
            where rs.reservationId in :reservationIds
              and rs.status = :fromStatus
            """)
    int expireByReservationIds(
            @Param("reservationIds") Collection<UUID> reservationIds,
            @Param("fromStatus") ReservationSeatStatus fromStatus,
            @Param("toStatus") ReservationSeatStatus toStatus,
            @Param("now") Instant now
    );
}

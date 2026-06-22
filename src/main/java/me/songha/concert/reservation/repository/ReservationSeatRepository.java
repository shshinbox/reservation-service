package me.songha.concert.reservation.repository;

import me.songha.concert.reservation.domain.ReservationSeat;
import me.songha.concert.reservation.domain.ReservationStatus;
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
            Collection<ReservationStatus> statuses
    );

    List<ReservationSeat> findByReservationIdOrderBySeatIdAsc(UUID reservationId);

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
            @Param("fromStatus") ReservationStatus fromStatus,
            @Param("toStatus") ReservationStatus toStatus,
            @Param("now") Instant now
    );
}

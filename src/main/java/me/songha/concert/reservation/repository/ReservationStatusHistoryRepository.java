package me.songha.concert.reservation.repository;

import me.songha.concert.reservation.domain.ReservationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface ReservationStatusHistoryRepository extends JpaRepository<ReservationStatusHistory, Long> {

    @Modifying
    @Query(value = """
            insert into reservation_status_histories (
                reservation_id,
                from_status,
                to_status,
                reason,
                changed_at
            )
            select r.reservation_id, :fromStatus, :toStatus, :reason, :changedAt
            from reservations r
            where r.reservation_id in (:reservationIds)
              and r.status = :fromStatus
            """, nativeQuery = true)
    int insertExpirationHistories(
            @Param("reservationIds") Collection<UUID> reservationIds,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("reason") String reason,
            @Param("changedAt") Instant changedAt
    );
}

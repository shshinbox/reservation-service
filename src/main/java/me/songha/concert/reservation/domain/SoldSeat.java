package me.songha.concert.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "sold_seats")
public class SoldSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "schedule_id", nullable = false, length = 100, updatable = false)
    private String scheduleId;

    @Column(name = "seat_id", nullable = false, length = 100, updatable = false)
    private String seatId;

    @Column(name = "user_id", nullable = false, length = 100, updatable = false)
    private String userId;

    @Column(name = "sold_at", nullable = false, updatable = false)
    private Instant soldAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private SoldSeat(UUID reservationId, String scheduleId, String seatId, String userId, Instant soldAt) {
        this.reservationId = reservationId;
        this.scheduleId = scheduleId;
        this.seatId = seatId;
        this.userId = userId;
        this.soldAt = soldAt;
    }

    public static SoldSeat sold(Reservation reservation, Instant soldAt) {
        return new SoldSeat(
                reservation.getReservationId(),
                reservation.getScheduleId(),
                reservation.getSeatId(),
                reservation.getUserId(),
                soldAt
        );
    }

    public void cancel(Instant now) {
        if (cancelledAt == null) {
            this.cancelledAt = now;
        }
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}

package me.songha.concert.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reservation_seats")
public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "schedule_id", nullable = false, length = 100, updatable = false)
    private String scheduleId;

    @Column(name = "seat_id", nullable = false, length = 100, updatable = false)
    private String seatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReservationSeatStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ReservationSeat(UUID reservationId, String scheduleId, String seatId, Instant now) {
        this.reservationId = reservationId;
        this.scheduleId = scheduleId;
        this.seatId = seatId;
        this.status = ReservationSeatStatus.HOLD;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ReservationSeat paymentPending(Reservation reservation, String seatId, Instant now) {
        return new ReservationSeat(reservation.getReservationId(), reservation.getScheduleId(), seatId, now);
    }

    public void confirm(Instant now) {
        if (status == ReservationSeatStatus.HOLD) {
            this.status = ReservationSeatStatus.RESERVED;
            this.updatedAt = now;
        }
    }

    public void cancel(Instant now) {
        if (status == ReservationSeatStatus.HOLD || status == ReservationSeatStatus.RESERVED) {
            this.status = ReservationSeatStatus.CANCELLED;
            this.updatedAt = now;
        }
    }

    public void expire(Instant now) {
        if (status == ReservationSeatStatus.HOLD) {
            this.status = ReservationSeatStatus.EXPIRED;
            this.updatedAt = now;
        }
    }
}

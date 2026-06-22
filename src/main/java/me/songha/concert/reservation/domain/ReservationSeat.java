package me.songha.concert.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ReservationSeat(UUID reservationId, String scheduleId, String seatId) {
        this.reservationId = reservationId;
        this.scheduleId = scheduleId;
        this.seatId = seatId;
        this.status = ReservationStatus.PAYMENT_PENDING;
    }

    public static ReservationSeat paymentPending(Reservation reservation, String seatId) {
        return new ReservationSeat(reservation.getReservationId(), reservation.getScheduleId(), seatId);
    }

    public void confirm() {
        if (status == ReservationStatus.PAYMENT_PENDING) {
            this.status = ReservationStatus.CONFIRMED;
        }
    }

    public void cancel() {
        if (status == ReservationStatus.PAYMENT_PENDING || status == ReservationStatus.CONFIRMED) {
            this.status = ReservationStatus.CANCELLED;
        }
    }

    public void expire() {
        if (status == ReservationStatus.PAYMENT_PENDING) {
            this.status = ReservationStatus.EXPIRED;
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

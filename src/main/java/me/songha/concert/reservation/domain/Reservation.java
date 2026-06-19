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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, unique = true, updatable = false)
    private UUID reservationId;

    @Column(name = "hold_id", nullable = false, unique = true, updatable = false)
    private UUID holdId;

    @Column(name = "schedule_id", nullable = false, length = 100, updatable = false)
    private String scheduleId;

    @Column(name = "seat_id", nullable = false, length = 100, updatable = false)
    private String seatId;

    @Column(name = "user_id", nullable = false, length = 100, updatable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReservationStatus status;

    @Column(name = "hold_expires_at", nullable = false, updatable = false)
    private Instant holdExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private Reservation(
            UUID reservationId,
            UUID holdId,
            String scheduleId,
            String seatId,
            String userId,
            Instant holdExpiresAt
    ) {
        this.reservationId = reservationId;
        this.holdId = holdId;
        this.scheduleId = scheduleId;
        this.seatId = seatId;
        this.userId = userId;
        this.status = ReservationStatus.PAYMENT_PENDING;
        this.holdExpiresAt = holdExpiresAt;
    }

    public static Reservation paymentPending(
            UUID holdId,
            String scheduleId,
            String seatId,
            String userId,
            Instant holdExpiresAt
    ) {
        return new Reservation(UUID.randomUUID(), holdId, scheduleId, seatId, userId, holdExpiresAt);
    }

    public void confirm(Instant now) {
        if (status != ReservationStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Only PAYMENT_PENDING reservations can be confirmed.");
        }
        if (!now.isBefore(holdExpiresAt)) {
            throw new IllegalStateException("Reservation hold is expired.");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public void cancel(Instant now) {
        if (status == ReservationStatus.CANCELLED) {
            return;
        }
        if (status != ReservationStatus.PAYMENT_PENDING && status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Only PAYMENT_PENDING or CONFIRMED reservations can be cancelled.");
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void expire(Instant now) {
        if (status != ReservationStatus.PAYMENT_PENDING) {
            return;
        }
        this.status = ReservationStatus.EXPIRED;
        this.expiredAt = now;
    }

    public boolean isHoldExpired(Instant now) {
        return !now.isBefore(holdExpiresAt);
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

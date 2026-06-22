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
import java.util.List;
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

    @Column(name = "confirmation_id", nullable = false, unique = true, length = 100, updatable = false)
    private String confirmationId;

    @Column(name = "schedule_id", nullable = false, length = 100, updatable = false)
    private String scheduleId;

    @Column(name = "user_id", nullable = false, length = 100, updatable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReservationStatus status;

    @Column(name = "payment_expires_at", nullable = false, updatable = false)
    private Instant paymentExpiresAt;

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

    private Reservation(
            UUID reservationId,
            String confirmationId,
            String scheduleId,
            String userId,
            Instant paymentExpiresAt
    ) {
        this.reservationId = reservationId;
        this.confirmationId = confirmationId;
        this.scheduleId = scheduleId;
        this.userId = userId;
        this.status = ReservationStatus.PAYMENT_PENDING;
        this.paymentExpiresAt = paymentExpiresAt;
    }

    public static Reservation paymentPending(
            String confirmationId,
            String scheduleId,
            String userId,
            Instant paymentExpiresAt
    ) {
        return new Reservation(UUID.randomUUID(), confirmationId, scheduleId, userId, paymentExpiresAt);
    }

    public List<ReservationSeat> createSeats(List<String> seatIds) {
        if (status != ReservationStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Only PAYMENT_PENDING reservations can create seats.");
        }
        return seatIds.stream()
                .map(seatId -> ReservationSeat.paymentPending(this, seatId))
                .toList();
    }

    public void confirm(Instant now) {
        if (status != ReservationStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Only PAYMENT_PENDING reservations can be confirmed.");
        }
        if (!now.isBefore(paymentExpiresAt)) {
            throw new IllegalStateException("Reservation hold is expired.");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public void cancel(Instant now) {
        if (status == ReservationStatus.CANCELLED) {
            return;
        }
        if (status != ReservationStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Only PAYMENT_PENDING reservations can be cancelled.");
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

    public boolean isPaymentExpired(Instant now) {
        return !now.isBefore(paymentExpiresAt);
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

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

import java.time.Instant;
import java.util.UUID;

@Entity
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

    @Column(name = "venue_id", nullable = false, length = 100, updatable = false)
    private String venueId;

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

    protected Reservation() {
    }

    private Reservation(
            UUID reservationId,
            UUID holdId,
            String scheduleId,
            String venueId,
            String seatId,
            String userId,
            Instant holdExpiresAt
    ) {
        this.reservationId = reservationId;
        this.holdId = holdId;
        this.scheduleId = scheduleId;
        this.venueId = venueId;
        this.seatId = seatId;
        this.userId = userId;
        this.status = ReservationStatus.HOLDING;
        this.holdExpiresAt = holdExpiresAt;
    }

    public static Reservation holding(
            UUID holdId,
            String scheduleId,
            String venueId,
            String seatId,
            String userId,
            Instant holdExpiresAt
    ) {
        return new Reservation(UUID.randomUUID(), holdId, scheduleId, venueId, seatId, userId, holdExpiresAt);
    }

    public void confirm(Instant now) {
        if (status != ReservationStatus.HOLDING) {
            throw new IllegalStateException("Only HOLDING reservations can be confirmed.");
        }
        if (!now.isBefore(holdExpiresAt)) {
            throw new IllegalStateException("Reservation hold is expired.");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public void cancel(Instant now) {
        if (status != ReservationStatus.HOLDING) {
            throw new IllegalStateException("Only HOLDING reservations can be cancelled.");
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void expire(Instant now) {
        if (status != ReservationStatus.HOLDING) {
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

    public Long getId() {
        return id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getHoldId() {
        return holdId;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public String getVenueId() {
        return venueId;
    }

    public String getSeatId() {
        return seatId;
    }

    public String getUserId() {
        return userId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}

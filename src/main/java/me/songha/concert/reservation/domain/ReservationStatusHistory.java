package me.songha.concert.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation_status_histories")
public class ReservationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30, updatable = false)
    private ReservationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30, updatable = false)
    private ReservationStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 100, updatable = false)
    private ReservationStatusChangeReason reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected ReservationStatusHistory() {
    }

    public ReservationStatusHistory(
            UUID reservationId,
            ReservationStatus fromStatus,
            ReservationStatus toStatus,
            ReservationStatusChangeReason reason
    ) {
        this.reservationId = reservationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
    }

    @PrePersist
    void prePersist() {
        this.changedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public ReservationStatus getFromStatus() {
        return fromStatus;
    }

    public ReservationStatus getToStatus() {
        return toStatus;
    }

    public ReservationStatusChangeReason getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}

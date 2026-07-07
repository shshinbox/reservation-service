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

    public ReservationStatusHistory(
            UUID reservationId,
            ReservationStatus fromStatus,
            ReservationStatus toStatus,
            ReservationStatusChangeReason reason,
            Instant changedAt
    ) {
        this.reservationId = reservationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedAt = changedAt;
    }
}

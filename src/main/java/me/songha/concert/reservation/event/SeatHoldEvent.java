package me.songha.concert.reservation.event;

import java.time.Instant;
import java.util.UUID;

public record SeatHoldEvent(
        UUID eventId,
        String eventType,
        UUID holdId,
        String scheduleId,
        String seatId,
        String userId,
        Instant expiresAt,
        Instant occurredAt
) {
}

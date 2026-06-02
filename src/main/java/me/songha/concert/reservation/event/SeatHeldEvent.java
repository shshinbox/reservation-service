package me.songha.concert.reservation.event;

import java.time.Instant;
import java.util.UUID;

public record SeatHeldEvent(
        UUID eventId,
        String eventType,
        UUID holdId,
        String scheduleId,
        String venueId,
        String seatId,
        String userId,
        Instant holdExpiresAt,
        Instant occurredAt
) {
}

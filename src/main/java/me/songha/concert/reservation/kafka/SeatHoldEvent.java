package me.songha.concert.reservation.kafka;

import java.time.Instant;
import java.util.List;

public record SeatHoldEvent(
        String eventId,
        String eventType,
        String holdId,
        String scheduleId,
        List<String> seatIds,
        String userId,
        Instant expiresAt,
        Instant occurredAt,
        Integer schemaVersion
) {
}

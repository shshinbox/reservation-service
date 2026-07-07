package me.songha.concert.schedule;

import java.time.Instant;

public record ScheduleInfo(
        String scheduleId,
        Instant startsAt,
        Instant endsAt,
        Instant reservableUntil
) {

    public boolean isReservableAt(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null.");
        }
        Instant deadline = reservableUntil != null ? reservableUntil : startsAt;
        return deadline != null && now.isBefore(deadline);
    }
}

package me.songha.concert.time;

import java.time.Instant;

public record TimeOverrideSettings(
        boolean enabled,
        Instant fixedInstant,
        String zoneId
) {
}

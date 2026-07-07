package me.songha.concert.time;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AppTimeProvider {

    private final TimeOverrideRepository repository;
    private final Environment environment;

    public Instant nowInstant() {
        if (isProd()) {
            return Instant.now();
        }

        TimeOverrideSettings config = repository.get();

        if (config == null || !config.enabled() || config.fixedInstant() == null) {
            return Instant.now();
        }

        return config.fixedInstant();
    }

    public ZonedDateTime nowZoned() {
        if (isProd()) {
            return Instant.now().atZone(ZoneOffset.UTC);
        }

        TimeOverrideSettings config = repository.get();
        ZoneId zoneId = resolveZone(config);

        return resolveInstant(config).atZone(zoneId);
    }

    public LocalDateTime nowLocal() {
        return nowZoned().toLocalDateTime();
    }

    private Instant resolveInstant(TimeOverrideSettings config) {
        if (config == null || !config.enabled() || config.fixedInstant() == null) {
            return Instant.now();
        }
        return config.fixedInstant();
    }

    private ZoneId resolveZone(TimeOverrideSettings config) {
        if (config != null && config.zoneId() != null) {
            return ZoneId.of(config.zoneId());
        }
        return ZoneOffset.UTC;
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}

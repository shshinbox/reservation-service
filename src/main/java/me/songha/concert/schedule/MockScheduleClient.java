package me.songha.concert.schedule;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile("local")
public class MockScheduleClient implements ScheduleClient {

    @Override
    public ScheduleInfo getSchedule(String scheduleId) {
        return new ScheduleInfo(
                scheduleId,
                Instant.parse("2026-07-10T14:00:00Z"),
                Instant.parse("2026-07-10T20:00:00Z"),
                Instant.parse("2026-07-10T00:00:00Z")
        );
    }
}

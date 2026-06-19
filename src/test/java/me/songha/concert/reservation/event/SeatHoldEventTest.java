package me.songha.concert.reservation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SeatHoldEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void mapsSeatHoldEventJson() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();

        SeatHoldEvent event = objectMapper.readValue("""
                {
                  "eventId": "%s",
                  "eventType": "SEAT_HOLD_HELD",
                  "holdId": "%s",
                  "scheduleId": "schedule-1",
                  "seatId": "A-12",
                  "userId": "user-1",
                  "expiresAt": "2026-05-25T11:55:00Z",
                  "occurredAt": "2026-05-25T11:50:00Z"
                }
                """.formatted(eventId, holdId), SeatHoldEvent.class);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo("SEAT_HOLD_HELD");
        assertThat(event.holdId()).isEqualTo(holdId);
        assertThat(event.scheduleId()).isEqualTo("schedule-1");
        assertThat(event.seatId()).isEqualTo("A-12");
        assertThat(event.userId()).isEqualTo("user-1");
        assertThat(event.expiresAt()).isEqualTo(Instant.parse("2026-05-25T11:55:00Z"));
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-05-25T11:50:00Z"));
    }
}

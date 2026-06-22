package me.songha.concert.reservation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.songha.concert.reservation.kafka.SeatHoldEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeatHoldEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void mapsSeatHoldEventJson() throws Exception {
        SeatHoldEvent event = objectMapper.readValue("""
                {
                  "eventId": "event-1",
                  "eventType": "SEAT_HOLD_CONFIRMED",
                  "holdId": "confirmation-1",
                  "scheduleId": "schedule-1",
                  "seatIds": ["A-12", "A-13"],
                  "userId": "user-1",
                  "expiresAt": null,
                  "occurredAt": "2026-05-25T11:50:00Z",
                  "schemaVersion": 2
                }
                """, SeatHoldEvent.class);

        assertThat(event.eventId()).isEqualTo("event-1");
        assertThat(event.eventType()).isEqualTo("SEAT_HOLD_CONFIRMED");
        assertThat(event.holdId()).isEqualTo("confirmation-1");
        assertThat(event.scheduleId()).isEqualTo("schedule-1");
        assertThat(event.seatIds()).isEqualTo(List.of("A-12", "A-13"));
        assertThat(event.userId()).isEqualTo("user-1");
        assertThat(event.expiresAt()).isNull();
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-05-25T11:50:00Z"));
        assertThat(event.schemaVersion()).isEqualTo(2);
    }
}

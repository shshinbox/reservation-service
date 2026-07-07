package me.songha.concert.reservation.dto;

import me.songha.concert.consumer.KafkaEventMetadata;
import me.songha.concert.consumer.SeatHoldEvent;

import java.time.Instant;
import java.util.List;

public record CreateReservationDraftCommand(
        String eventId,
        String eventType,
        String holdId,
        String scheduleId,
        List<String> seatIds,
        String userId,
        Instant occurredAt,
        String topic,
        int partitionNo,
        long offsetNo
) {
    public static CreateReservationDraftCommand from(SeatHoldEvent event, KafkaEventMetadata metadata) {
        return new CreateReservationDraftCommand(
                event.eventId(),
                event.eventType(),
                event.holdId(),
                event.scheduleId(),
                event.seatIds(),
                event.userId(),
                event.occurredAt(),
                metadata.topic(),
                metadata.partitionNo(),
                metadata.offsetNo()
        );
    }
}
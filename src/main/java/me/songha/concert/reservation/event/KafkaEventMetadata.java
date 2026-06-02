package me.songha.concert.reservation.event;

public record KafkaEventMetadata(
        String topic,
        int partitionNo,
        long offsetNo
) {
}

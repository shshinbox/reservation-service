package me.songha.concert.reservation.kafka;

public record KafkaEventMetadata(
        String topic,
        int partitionNo,
        long offsetNo
) {
}

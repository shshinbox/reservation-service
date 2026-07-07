package me.songha.concert.consumer;

public record KafkaEventMetadata(
        String topic,
        int partitionNo,
        long offsetNo
) {
}

package me.songha.concert.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_kafka_events")
public class ProcessedKafkaEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 255, updatable = false)
    private String topic;

    @Column(name = "partition_no", nullable = false, updatable = false)
    private int partitionNo;

    @Column(name = "offset_no", nullable = false, updatable = false)
    private long offsetNo;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedKafkaEvent() {
    }

    public ProcessedKafkaEvent(UUID eventId, String eventType, String topic, int partitionNo, long offsetNo) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.topic = topic;
        this.partitionNo = partitionNo;
        this.offsetNo = offsetNo;
    }

    @PrePersist
    void prePersist() {
        this.processedAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public int getPartitionNo() {
        return partitionNo;
    }

    public long getOffsetNo() {
        return offsetNo;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}

package me.songha.concert.reservation.kafka;

import me.songha.concert.reservation.service.ReservationDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeatHoldEventConsumer {

    private final ReservationDraftService reservationDraftService;

    @KafkaListener(
            topics = "${reservation.kafka.topics.seat-hold}",
            groupId = "${reservation.kafka.consumer.group-id}",
            containerFactory = "seatHoldKafkaListenerContainerFactory"
    )
    public void consume(
            SeatHoldEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partitionNo,
            @Header(KafkaHeaders.OFFSET) long offsetNo
    ) {
        reservationDraftService.applySeatHoldEvent(
                event,
                new KafkaEventMetadata(topic, partitionNo, offsetNo)
        );
    }
}

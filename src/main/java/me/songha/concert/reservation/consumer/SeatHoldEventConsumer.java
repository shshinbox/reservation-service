package me.songha.concert.reservation.consumer;

import me.songha.concert.reservation.application.ReservationDraftService;
import me.songha.concert.reservation.application.ReservationDuplicateMessageResolver;
import me.songha.concert.reservation.event.KafkaEventMetadata;
import me.songha.concert.reservation.event.SeatHoldEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeatHoldEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldEventConsumer.class);

    private final ReservationDraftService reservationDraftService;
    private final ReservationDuplicateMessageResolver duplicateMessageResolver;

    @KafkaListener(
            topics = "${reservation.kafka.topics.seat-hold}",
            groupId = "${reservation.kafka.consumer.group-id}",
            containerFactory = "seatHoldKafkaListenerContainerFactory"
    )
    public void consume(
            SeatHoldEvent event,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partitionNo,
            @Header(KafkaHeaders.OFFSET) long offsetNo
    ) {
        try {
            reservationDraftService.applySeatHoldEvent(
                    event,
                    new KafkaEventMetadata(topic, partitionNo, offsetNo)
            );
            acknowledgment.acknowledge();
        } catch (DataIntegrityViolationException exception) {
            if (duplicateMessageResolver.isAlreadyProcessed(exception)) {
                log.info(
                        "SeatHoldEvent already processed. eventId={}, holdId={}, topic={}, partition={}, offset={}",
                        event != null ? event.eventId() : null,
                        event != null ? event.holdId() : null,
                        topic,
                        partitionNo,
                        offsetNo
                );
                acknowledgment.acknowledge();
                return;
            }
            throw exception;
        }
    }
}

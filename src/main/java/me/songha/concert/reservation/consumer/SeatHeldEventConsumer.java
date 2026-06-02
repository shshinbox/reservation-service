package me.songha.concert.reservation.consumer;

import me.songha.concert.reservation.application.ReservationDraftService;
import me.songha.concert.reservation.application.ReservationDuplicateMessageResolver;
import me.songha.concert.reservation.event.KafkaEventMetadata;
import me.songha.concert.reservation.event.SeatHeldEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class SeatHeldEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeatHeldEventConsumer.class);

    private final ReservationDraftService reservationDraftService;
    private final ReservationDuplicateMessageResolver duplicateMessageResolver;

    public SeatHeldEventConsumer(
            ReservationDraftService reservationDraftService,
            ReservationDuplicateMessageResolver duplicateMessageResolver
    ) {
        this.reservationDraftService = reservationDraftService;
        this.duplicateMessageResolver = duplicateMessageResolver;
    }

    @KafkaListener(
            topics = "${reservation.kafka.topics.seat-held}",
            groupId = "${reservation.kafka.consumer.group-id}",
            containerFactory = "seatHeldKafkaListenerContainerFactory"
    )
    public void consume(
            SeatHeldEvent event,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partitionNo,
            @Header(KafkaHeaders.OFFSET) long offsetNo
    ) {
        try {
            reservationDraftService.createDraftFromSeatHeldEvent(
                    event,
                    new KafkaEventMetadata(topic, partitionNo, offsetNo)
            );
            acknowledgment.acknowledge();
        } catch (DataIntegrityViolationException exception) {
            if (duplicateMessageResolver.isAlreadyProcessed(exception)) {
                log.info(
                        "SeatHeldEvent already processed. eventId={}, holdId={}, topic={}, partition={}, offset={}",
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

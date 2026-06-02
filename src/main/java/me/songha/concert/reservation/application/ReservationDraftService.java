package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.ProcessedKafkaEvent;
import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.domain.ReservationStatusChangeReason;
import me.songha.concert.reservation.domain.ReservationStatusHistory;
import me.songha.concert.reservation.event.KafkaEventMetadata;
import me.songha.concert.reservation.event.SeatHeldEvent;
import me.songha.concert.reservation.repository.ProcessedKafkaEventRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationDraftService {

    private static final String SEAT_HELD_EVENT_TYPE = "SEAT_HELD";

    private final ProcessedKafkaEventRepository processedKafkaEventRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository statusHistoryRepository;

    public ReservationDraftService(
            ProcessedKafkaEventRepository processedKafkaEventRepository,
            ReservationRepository reservationRepository,
            ReservationStatusHistoryRepository statusHistoryRepository
    ) {
        this.processedKafkaEventRepository = processedKafkaEventRepository;
        this.reservationRepository = reservationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    @Transactional
    public void createDraftFromSeatHeldEvent(SeatHeldEvent event, KafkaEventMetadata metadata) {
        validate(event);

        processedKafkaEventRepository.saveAndFlush(new ProcessedKafkaEvent(
                event.eventId(),
                event.eventType(),
                metadata.topic(),
                metadata.partitionNo(),
                metadata.offsetNo()
        ));

        if (reservationRepository.existsByHoldId(event.holdId())) {
            return;
        }

        Reservation reservation = Reservation.holding(
                event.holdId(),
                event.scheduleId(),
                event.venueId(),
                event.seatId(),
                event.userId(),
                event.holdExpiresAt()
        );

        reservationRepository.saveAndFlush(reservation);

        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                null,
                ReservationStatus.HOLDING,
                ReservationStatusChangeReason.SEAT_HELD_EVENT
        ));
    }

    private void validate(SeatHeldEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("SeatHeldEvent must not be null.");
        }
        if (event.eventId() == null) {
            throw new IllegalArgumentException("eventId must not be null.");
        }
        if (!SEAT_HELD_EVENT_TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException("eventType must be SEAT_HELD.");
        }
        if (event.holdId() == null) {
            throw new IllegalArgumentException("holdId must not be null.");
        }
        if (isBlank(event.scheduleId())) {
            throw new IllegalArgumentException("scheduleId must not be blank.");
        }
        if (isBlank(event.venueId())) {
            throw new IllegalArgumentException("venueId must not be blank.");
        }
        if (isBlank(event.seatId())) {
            throw new IllegalArgumentException("seatId must not be blank.");
        }
        if (isBlank(event.userId())) {
            throw new IllegalArgumentException("userId must not be blank.");
        }
        if (event.holdExpiresAt() == null) {
            throw new IllegalArgumentException("holdExpiresAt must not be null.");
        }
        if (event.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt must not be null.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

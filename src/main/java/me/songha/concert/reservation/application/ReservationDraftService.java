package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.ProcessedKafkaEvent;
import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.domain.ReservationStatusChangeReason;
import me.songha.concert.reservation.domain.ReservationStatusHistory;
import me.songha.concert.reservation.event.KafkaEventMetadata;
import me.songha.concert.reservation.event.SeatHoldEvent;
import me.songha.concert.reservation.repository.ProcessedKafkaEventRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationDraftService {

    private static final String SEAT_HOLD_HELD_EVENT_TYPE = "SEAT_HOLD_HELD";
    private static final String SEAT_HOLD_RELEASED_EVENT_TYPE = "SEAT_HOLD_RELEASED";

    private final ProcessedKafkaEventRepository processedKafkaEventRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository statusHistoryRepository;

    @Transactional
    public void applySeatHoldEvent(SeatHoldEvent event, KafkaEventMetadata metadata) {
        validate(event);

        processedKafkaEventRepository.saveAndFlush(new ProcessedKafkaEvent(
                event.eventId(),
                event.eventType(),
                metadata.topic(),
                metadata.partitionNo(),
                metadata.offsetNo()
        ));

        if (SEAT_HOLD_RELEASED_EVENT_TYPE.equals(event.eventType())) {
            releasePaymentPendingReservation(event);
            return;
        }

        if (reservationRepository.existsByHoldId(event.holdId())) {
            return;
        }

        Reservation reservation = Reservation.paymentPending(
                event.holdId(),
                event.scheduleId(),
                event.seatId(),
                event.userId(),
                event.expiresAt()
        );

        reservationRepository.saveAndFlush(reservation);

        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                null,
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatusChangeReason.SEAT_HOLD_HELD_EVENT
        ));
    }

    private void releasePaymentPendingReservation(SeatHoldEvent event) {
        reservationRepository.findByHoldIdForUpdate(event.holdId())
                .filter(reservation -> reservation.getStatus() == ReservationStatus.PAYMENT_PENDING)
                .ifPresent(reservation -> {
                    reservation.cancel(event.occurredAt());
                    statusHistoryRepository.save(new ReservationStatusHistory(
                            reservation.getReservationId(),
                            ReservationStatus.PAYMENT_PENDING,
                            ReservationStatus.CANCELLED,
                            ReservationStatusChangeReason.SEAT_HOLD_RELEASED_EVENT
                    ));
                });
    }

    private void validate(SeatHoldEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("SeatHoldEvent must not be null.");
        }
        if (event.eventId() == null) {
            throw new IllegalArgumentException("eventId must not be null.");
        }
        if (!SEAT_HOLD_HELD_EVENT_TYPE.equals(event.eventType())
                && !SEAT_HOLD_RELEASED_EVENT_TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException("eventType must be SEAT_HOLD_HELD or SEAT_HOLD_RELEASED.");
        }
        if (event.holdId() == null) {
            throw new IllegalArgumentException("holdId must not be null.");
        }
        if (isBlank(event.scheduleId())) {
            throw new IllegalArgumentException("scheduleId must not be blank.");
        }
        if (isBlank(event.seatId())) {
            throw new IllegalArgumentException("seatId must not be blank.");
        }
        if (isBlank(event.userId())) {
            throw new IllegalArgumentException("userId must not be blank.");
        }
        if (SEAT_HOLD_HELD_EVENT_TYPE.equals(event.eventType()) && event.expiresAt() == null) {
            throw new IllegalArgumentException("expiresAt must not be null.");
        }
        if (event.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt must not be null.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

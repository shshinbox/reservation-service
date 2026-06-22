package me.songha.concert.reservation.service;

import lombok.RequiredArgsConstructor;
import me.songha.concert.reservation.domain.*;
import me.songha.concert.reservation.kafka.KafkaEventMetadata;
import me.songha.concert.reservation.kafka.SeatHoldEvent;
import me.songha.concert.reservation.kafka.SeatHoldEventConflictException;
import me.songha.concert.reservation.repository.ProcessedKafkaEventRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationDraftService {

    private static final String SEAT_HOLD_CONFIRMED_EVENT_TYPE = "SEAT_HOLD_CONFIRMED";
    private static final int MAX_SEAT_COUNT = 4;
    private static final Duration PAYMENT_PENDING_DURATION = Duration.ofDays(3);
    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(
            ReservationStatus.PAYMENT_PENDING,
            ReservationStatus.CONFIRMED
    );

    private final ProcessedKafkaEventRepository processedKafkaEventRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ReservationStatusHistoryRepository statusHistoryRepository;

    @Transactional
    public void applySeatHoldEvent(SeatHoldEvent event, KafkaEventMetadata metadata) {
        validate(event);
        List<String> seatIds = normalizeSeatIds(event.seatIds());

        if (processedKafkaEventRepository.existsById(event.eventId())) {
            return;
        }

        processedKafkaEventRepository.save(new ProcessedKafkaEvent(
                event.eventId(),
                event.eventType(),
                metadata.topic(),
                metadata.partitionNo(),
                metadata.offsetNo()
        ));

        if (reservationRepository.existsByConfirmationId(event.holdId())) {
            return;
        }

        if (hasActiveReservationSeat(event.scheduleId(), seatIds)) {
            throw new SeatHoldEventConflictException(
                    "Active reservation seat already exists. scheduleId=%s, seatIds=%s"
                            .formatted(event.scheduleId(), seatIds)
            );
        }

        Instant paymentExpiresAt = event.occurredAt().plus(PAYMENT_PENDING_DURATION);
        Reservation reservation = Reservation.paymentPending(
                event.holdId(),
                event.scheduleId(),
                event.userId(),
                paymentExpiresAt
        );

        reservationRepository.save(reservation);
        reservationSeatRepository.saveAll(reservation.createSeats(seatIds));

        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                null,
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatusChangeReason.SEAT_HOLD_CONFIRMED_EVENT
        ));
    }

    private boolean hasActiveReservationSeat(String scheduleId, List<String> seatIds) {
        return seatIds
                .stream()
                .anyMatch(seatId -> reservationSeatRepository.existsByScheduleIdAndSeatIdAndStatusIn(
                        scheduleId,
                        seatId,
                        ACTIVE_STATUSES
                ));
    }

    private void validate(SeatHoldEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("SeatHoldEvent must not be null.");
        }
        if (isBlank(event.eventId())) {
            throw new IllegalArgumentException("eventId must not be blank.");
        }
        if (!SEAT_HOLD_CONFIRMED_EVENT_TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException("eventType must be SEAT_HOLD_CONFIRMED.");
        }
        if (isBlank(event.holdId())) {
            throw new IllegalArgumentException("holdId must not be blank.");
        }
        if (isBlank(event.scheduleId())) {
            throw new IllegalArgumentException("scheduleId must not be blank.");
        }
        if (event.seatIds() == null || event.seatIds().isEmpty()) {
            throw new IllegalArgumentException("seatIds must not be empty.");
        }
        List<String> seatIds = normalizeSeatIds(event.seatIds());
        if (seatIds.size() > MAX_SEAT_COUNT) {
            throw new IllegalArgumentException("seatIds must contain at most 4 seats.");
        }
        if (seatIds.stream().anyMatch(this::isBlank)) {
            throw new IllegalArgumentException("seatIds must not contain blank value.");
        }
        if (Set.copyOf(seatIds).size() != seatIds.size()) {
            throw new IllegalArgumentException("seatIds must not contain duplicated value.");
        }
        if (isBlank(event.userId())) {
            throw new IllegalArgumentException("userId must not be blank.");
        }
        if (event.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt must not be null.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> normalizeSeatIds(List<String> seatIds) {
        return seatIds.stream()
                .map(String::trim)
                .toList();
    }
}

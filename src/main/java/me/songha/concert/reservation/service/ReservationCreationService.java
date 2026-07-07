package me.songha.concert.reservation.service;

import lombok.RequiredArgsConstructor;
import me.songha.concert.consumer.SeatHoldEventConflictException;
import me.songha.concert.reservation.domain.*;
import me.songha.concert.reservation.dto.CreateReservationDraftCommand;
import me.songha.concert.reservation.repository.ProcessedKafkaEventRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import me.songha.concert.schedule.ScheduleClient;
import me.songha.concert.schedule.ScheduleInfo;
import me.songha.concert.time.AppTimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationCreationService {

    private static final int MAX_SEAT_COUNT = 4;
    private static final Duration PAYMENT_PENDING_DURATION = Duration.ofDays(3);
    private static final List<ReservationSeatStatus> ACTIVE_SEAT_STATUSES = List.of(
            ReservationSeatStatus.HOLD,
            ReservationSeatStatus.RESERVED
    );

    private final ProcessedKafkaEventRepository processedKafkaEventRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ReservationStatusHistoryRepository statusHistoryRepository;
    private final ReservationPaymentPort reservationPaymentPort;
    private final ScheduleClient scheduleClient;
    private final AppTimeProvider appTimeProvider;

    @Transactional
    public void createPending(CreateReservationDraftCommand command) {
        validate(command);
        List<String> seatIds = normalizeSeatIds(command.seatIds());
        Instant now = appTimeProvider.nowInstant();
        validateScheduleReservable(command.scheduleId(), now);

        if (processedKafkaEventRepository.existsById(command.eventId())) {
            return;
        }

        processedKafkaEventRepository.save(new ProcessedKafkaEvent(
                command.eventId(),
                command.eventType(),
                command.topic(),
                command.partitionNo(),
                command.offsetNo(),
                now
        ));

        if (reservationRepository.existsByConfirmationId(command.holdId())) {
            return;
        }

        if (hasActiveReservationSeat(command.scheduleId(), seatIds)) {
            throw new SeatHoldEventConflictException(
                    "Active reservation seat already exists. scheduleId=%s, seatIds=%s"
                            .formatted(command.scheduleId(), seatIds)
            );
        }

        Instant paymentExpiresAt = command.occurredAt().plus(PAYMENT_PENDING_DURATION);
        Reservation reservation = Reservation.paymentPending(
                command.holdId(),
                command.scheduleId(),
                command.userId(),
                paymentExpiresAt,
                now
        );

        reservationRepository.save(reservation);
        reservationSeatRepository.saveAll(reservation.createSeats(seatIds, now));
        reservationPaymentPort.createReadyPayment(reservation.getReservationId(), now);

        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                null,
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatusChangeReason.SEAT_HOLD_CONFIRMED_EVENT,
                now
        ));
    }

    private void validateScheduleReservable(String scheduleId, Instant now) {
        ScheduleInfo schedule = scheduleClient.getSchedule(scheduleId);
        if (schedule == null || !schedule.isReservableAt(now)) {
            throw new IllegalStateException("Schedule is not reservable.");
        }
    }

    private boolean hasActiveReservationSeat(String scheduleId, List<String> seatIds) {
        return seatIds
                .stream()
                .anyMatch(seatId -> reservationSeatRepository.existsByScheduleIdAndSeatIdAndStatusIn(
                        scheduleId,
                        seatId,
                        ACTIVE_SEAT_STATUSES
                ));
    }

    private void validate(CreateReservationDraftCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("SeatHoldEvent must not be null.");
        }
        if (isBlank(command.eventId())) {
            throw new IllegalArgumentException("eventId must not be blank.");
        }
        if (isBlank(command.holdId())) {
            throw new IllegalArgumentException("holdId must not be blank.");
        }
        if (isBlank(command.scheduleId())) {
            throw new IllegalArgumentException("scheduleId must not be blank.");
        }
        if (command.seatIds() == null || command.seatIds().isEmpty()) {
            throw new IllegalArgumentException("seatIds must not be empty.");
        }
        List<String> seatIds = normalizeSeatIds(command.seatIds());
        if (seatIds.size() > MAX_SEAT_COUNT) {
            throw new IllegalArgumentException("seatIds must contain at most 4 seats.");
        }
        if (seatIds.stream().anyMatch(this::isBlank)) {
            throw new IllegalArgumentException("seatIds must not contain blank value.");
        }
        if (Set.copyOf(seatIds).size() != seatIds.size()) {
            throw new IllegalArgumentException("seatIds must not contain duplicated value.");
        }
        if (isBlank(command.userId())) {
            throw new IllegalArgumentException("userId must not be blank.");
        }
        if (command.occurredAt() == null) {
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

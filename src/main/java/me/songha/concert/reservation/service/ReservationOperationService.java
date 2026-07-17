package me.songha.concert.reservation.service;

import lombok.RequiredArgsConstructor;
import me.songha.concert.reservation.domain.*;
import me.songha.concert.reservation.dto.ReservationOperationResult;
import me.songha.concert.reservation.exception.ReservationAccessDeniedException;
import me.songha.concert.reservation.exception.ReservationConflictException;
import me.songha.concert.reservation.exception.ReservationNotFoundException;
import me.songha.concert.reservation.redis.SoldSeatRedisRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import me.songha.concert.time.AppTimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationOperationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatService reservationSeatService;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ReservationStatusHistoryRepository statusHistoryRepository;
    private final SoldSeatRedisRepository soldSeatRedisRepository;
    private final ReservationPaymentPort reservationPaymentPort;
    private final AppTimeProvider appTimeProvider;

    @Transactional
    public ReservationOperationResult confirm(UUID reservationId) {
        Reservation reservation = getReservationForUpdate(reservationId);
        List<ReservationSeat> seats = reservationSeatService.getSeats(reservation);
        Instant now = appTimeProvider.nowInstant();

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            markSoldOrThrow(reservation, seats);
            return ReservationOperationResult.completed(reservation, seats);
        }

        if (reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new ReservationConflictException("Only PAYMENT_PENDING reservations can be confirmed.");
        }

        if (reservation.isPaymentExpired(now)) {
            reservation.expire(now);
            seats.forEach(seat -> seat.expire(now));
            statusHistoryRepository.save(new ReservationStatusHistory(
                    reservation.getReservationId(),
                    ReservationStatus.PAYMENT_PENDING,
                    ReservationStatus.EXPIRED,
                    ReservationStatusChangeReason.HOLD_EXPIRED,
                    now
            ));
            return ReservationOperationResult.rejected(reservation, seats, "Reservation payment is expired.");
        }

        reservation.confirm(now);
        seats.forEach(seat -> seat.confirm(now));
        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatus.CONFIRMED,
                ReservationStatusChangeReason.PAYMENT_PAID,
                now
        ));
        markSoldOrThrow(reservation, seats);
        afterRollback(() -> soldSeatRedisRepository.deleteSold(reservation, seats));
        return ReservationOperationResult.completed(reservation, seats);
    }

    @Transactional
    public ReservationOperationResult cancel(UUID reservationId, String authenticatedUserId) {
        Reservation reservation = getReservationForUpdate(reservationId);
        validateOwner(reservation, authenticatedUserId);
        List<ReservationSeat> seats = reservationSeatService.getSeats(reservation);
        Instant now = appTimeProvider.nowInstant();

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            afterCommit(() -> soldSeatRedisRepository.deleteHold(reservation, seats));
            return ReservationOperationResult.completed(reservation, seats);
        }

        if (reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new ReservationConflictException("Only PAYMENT_PENDING reservations can be cancelled.");
        }

        ReservationStatus fromStatus = reservation.getStatus();
        reservation.cancel(now);
        seats.forEach(seat -> seat.cancel(now));
        reservationPaymentPort.cancelPayment(reservation.getReservationId(), now);
        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                fromStatus,
                ReservationStatus.CANCELLED,
                ReservationStatusChangeReason.USER_CANCELLED,
                now
        ));
        afterCommit(() -> soldSeatRedisRepository.deleteHold(reservation, seats));
        return ReservationOperationResult.completed(reservation, seats);
    }

    private void validateOwner(Reservation reservation, String authenticatedUserId) {
        if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
            throw new ReservationAccessDeniedException("Authenticated user id is required.");
        }
        if (!reservation.getUserId().equals(authenticatedUserId)) {
            throw new ReservationAccessDeniedException("Reservation owner mismatch.");
        }
    }

    private Reservation getReservationForUpdate(UUID reservationId) {
        return reservationRepository.findByReservationIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    private void markSoldOrThrow(Reservation reservation, List<ReservationSeat> seats) {
        try {
            markSoldWithRetry(reservation, seats);
        } catch (RuntimeException exception) {
            syncScheduleSoldOrThrow(reservation.getScheduleId(), exception);
        }
    }

    private void markSoldWithRetry(Reservation reservation, List<ReservationSeat> seats) {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                soldSeatRedisRepository.markSold(reservation, seats);
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure;
    }

    private void syncScheduleSoldOrThrow(String scheduleId, RuntimeException markSoldFailure) {
        try {
            List<ReservationSeat> soldSeats = reservationSeatRepository
                    .findByScheduleIdAndStatusOrderBySeatIdAsc(scheduleId, ReservationSeatStatus.RESERVED);
            soldSeatRedisRepository.syncScheduleSold(scheduleId, soldSeats);
        } catch (RuntimeException syncFailure) {
            syncFailure.addSuppressed(markSoldFailure);
            throw new ReservationConflictException("Failed to mark sold seats in Redis.", syncFailure);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void afterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }
}

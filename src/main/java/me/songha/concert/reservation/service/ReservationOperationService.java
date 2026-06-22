package me.songha.concert.reservation.service;

import me.songha.concert.reservation.exception.ReservationAccessDeniedException;
import me.songha.concert.reservation.exception.ReservationConflictException;
import me.songha.concert.reservation.exception.ReservationNotFoundException;
import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeat;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.domain.ReservationStatusChangeReason;
import me.songha.concert.reservation.domain.ReservationStatusHistory;
import me.songha.concert.reservation.redis.SoldSeatRedisRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import me.songha.concert.reservation.service.dto.ReservationOperationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationOperationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ReservationStatusHistoryRepository statusHistoryRepository;
    private final SoldSeatRedisRepository soldSeatRedisRepository;
    private final Clock clock;

    @Transactional
    public ReservationOperationResult confirmPaid(UUID reservationId) {
        Reservation reservation = getReservation(reservationId);
        List<ReservationSeat> seats = getSeats(reservation);
        Instant now = clock.instant();

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            afterCommit(() -> soldSeatRedisRepository.markSold(reservation, seats));
            return ReservationOperationResult.completed(reservation, seats);
        }

        if (reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new ReservationConflictException("Only PAYMENT_PENDING reservations can be confirmed.");
        }

        if (reservation.isPaymentExpired(now)) {
            reservation.expire(now);
            seats.forEach(ReservationSeat::expire);
            statusHistoryRepository.save(new ReservationStatusHistory(
                    reservation.getReservationId(),
                    ReservationStatus.PAYMENT_PENDING,
                    ReservationStatus.EXPIRED,
                    ReservationStatusChangeReason.HOLD_EXPIRED
            ));
            return ReservationOperationResult.rejected(reservation, seats, "Reservation payment is expired.");
        }

        reservation.confirm(now);
        for (ReservationSeat seat : seats) {
            seat.confirm();
        }
        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatus.CONFIRMED,
                ReservationStatusChangeReason.PAYMENT_PAID
        ));
        afterCommit(() -> soldSeatRedisRepository.markSold(reservation, seats));
        return ReservationOperationResult.completed(reservation, seats);
    }

    @Transactional
    public ReservationOperationResult cancel(UUID reservationId, String authenticatedUserId) {
        Reservation reservation = getReservation(reservationId);
        validateOwner(reservation, authenticatedUserId);
        List<ReservationSeat> seats = getSeats(reservation);
        Instant now = clock.instant();

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return ReservationOperationResult.completed(reservation, seats);
        }

        if (reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new ReservationConflictException("Only PAYMENT_PENDING reservations can be cancelled.");
        }

        ReservationStatus fromStatus = reservation.getStatus();
        reservation.cancel(now);
        seats.forEach(ReservationSeat::cancel);
        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                fromStatus,
                ReservationStatus.CANCELLED,
                ReservationStatusChangeReason.USER_CANCELLED
        ));
        return ReservationOperationResult.completed(reservation, seats);
    }

    @Transactional
    public int expireHoldingReservations() {
        Instant now = clock.instant();
        List<Reservation> reservations = reservationRepository
                .findTop100ByStatusAndPaymentExpiresAtBeforeOrderByPaymentExpiresAtAsc(
                        ReservationStatus.PAYMENT_PENDING,
                        now
                );
        if (reservations.isEmpty()) {
            return 0;
        }

        List<UUID> reservationIds = reservations.stream()
                .map(Reservation::getReservationId)
                .toList();
        int historyCount = statusHistoryRepository.insertExpirationHistories(
                reservationIds,
                ReservationStatus.PAYMENT_PENDING.name(),
                ReservationStatus.EXPIRED.name(),
                ReservationStatusChangeReason.HOLD_EXPIRED.name(),
                now
        );
        reservationSeatRepository.expireByReservationIds(
                reservationIds,
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatus.EXPIRED,
                now
        );
        reservationRepository.expireByReservationIds(
                reservationIds,
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatus.EXPIRED,
                now
        );

        return historyCount;
    }

    private Reservation getReservation(UUID reservationId) {
        return reservationRepository.findByReservationIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    private List<ReservationSeat> getSeats(Reservation reservation) {
        return reservationSeatRepository.findByReservationIdOrderBySeatIdAsc(reservation.getReservationId());
    }

    private void validateOwner(Reservation reservation, String authenticatedUserId) {
        if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
            throw new ReservationAccessDeniedException("Authenticated user id is required.");
        }
        if (!reservation.getUserId().equals(authenticatedUserId)) {
            throw new ReservationAccessDeniedException("Reservation owner mismatch.");
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
}

package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.domain.ReservationStatusChangeReason;
import me.songha.concert.reservation.domain.ReservationStatusHistory;
import me.songha.concert.reservation.domain.SoldSeat;
import me.songha.concert.reservation.infrastructure.redis.SoldSeatRedisRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import me.songha.concert.reservation.repository.SoldSeatRepository;
import lombok.RequiredArgsConstructor;
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
public class ReservationCommandService {

    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository statusHistoryRepository;
    private final SoldSeatRepository soldSeatRepository;
    private final SoldSeatRedisRepository soldSeatRedisRepository;
    private final Clock clock;

    @Transactional
    public ReservationCommandResult confirmPaid(UUID reservationId) {
        Reservation reservation = getReservation(reservationId);
        Instant now = clock.instant();

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            afterCommit(() -> soldSeatRedisRepository.markSold(reservation));
            return ReservationCommandResult.completed(reservation);
        }

        if (reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new ReservationConflictException("Only PAYMENT_PENDING reservations can be confirmed.");
        }

        if (reservation.isHoldExpired(now)) {
            reservation.expire(now);
            statusHistoryRepository.save(new ReservationStatusHistory(
                    reservation.getReservationId(),
                    ReservationStatus.PAYMENT_PENDING,
                    ReservationStatus.EXPIRED,
                    ReservationStatusChangeReason.HOLD_EXPIRED
            ));
            return ReservationCommandResult.rejected(reservation, "Reservation hold is expired.");
        }

        reservation.confirm(now);
        if (!soldSeatRepository.existsByReservationIdAndCancelledAtIsNull(reservation.getReservationId())) {
            soldSeatRepository.save(SoldSeat.sold(reservation, now));
        }
        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatus.CONFIRMED,
                ReservationStatusChangeReason.PAYMENT_PAID
        ));
        afterCommit(() -> soldSeatRedisRepository.markSold(reservation));
        return ReservationCommandResult.completed(reservation);
    }

    @Transactional
    public ReservationCommandResult cancel(UUID reservationId, String authenticatedUserId) {
        Reservation reservation = getReservation(reservationId);
        validateOwner(reservation, authenticatedUserId);
        Instant now = clock.instant();

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return ReservationCommandResult.completed(reservation);
        }

        if (reservation.getStatus() != ReservationStatus.PAYMENT_PENDING
                && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new ReservationConflictException("Only PAYMENT_PENDING or CONFIRMED reservations can be cancelled.");
        }

        ReservationStatus fromStatus = reservation.getStatus();
        reservation.cancel(now);
        if (fromStatus == ReservationStatus.CONFIRMED) {
            soldSeatRepository.findByReservationIdAndCancelledAtIsNull(reservation.getReservationId())
                    .ifPresent(soldSeat -> soldSeat.cancel(now));
            afterCommit(() -> soldSeatRedisRepository.deleteSold(reservation));
        }
        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                fromStatus,
                ReservationStatus.CANCELLED,
                ReservationStatusChangeReason.USER_CANCELLED
        ));
        return ReservationCommandResult.completed(reservation);
    }

    @Transactional
    public int expireHoldingReservations() {
        Instant now = clock.instant();
        List<Reservation> reservations = reservationRepository
                .findTop100ByStatusAndHoldExpiresAtBeforeOrderByHoldExpiresAtAsc(
                        ReservationStatus.PAYMENT_PENDING,
                        now
                );

        for (Reservation reservation : reservations) {
            reservation.expire(now);
            statusHistoryRepository.save(new ReservationStatusHistory(
                    reservation.getReservationId(),
                    ReservationStatus.PAYMENT_PENDING,
                    ReservationStatus.EXPIRED,
                    ReservationStatusChangeReason.HOLD_EXPIRED
            ));
        }

        return reservations.size();
    }

    private Reservation getReservation(UUID reservationId) {
        return reservationRepository.findByReservationIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
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

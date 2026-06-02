package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.domain.ReservationStatusChangeReason;
import me.songha.concert.reservation.domain.ReservationStatusHistory;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationCommandService {

    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository statusHistoryRepository;
    private final Clock clock;

    public ReservationCommandService(
            ReservationRepository reservationRepository,
            ReservationStatusHistoryRepository statusHistoryRepository,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.clock = clock;
    }

    @Transactional
    public ReservationCommandResult confirm(UUID reservationId, String authenticatedUserId) {
        Reservation reservation = getReservation(reservationId);
        validateOwner(reservation, authenticatedUserId);
        Instant now = clock.instant();

        if (reservation.getStatus() != ReservationStatus.HOLDING) {
            throw new ReservationConflictException("Only HOLDING reservations can be confirmed.");
        }

        if (reservation.isHoldExpired(now)) {
            reservation.expire(now);
            statusHistoryRepository.save(new ReservationStatusHistory(
                    reservation.getReservationId(),
                    ReservationStatus.HOLDING,
                    ReservationStatus.EXPIRED,
                    ReservationStatusChangeReason.HOLD_EXPIRED
            ));
            return ReservationCommandResult.rejected(reservation, "Reservation hold is expired.");
        }

        reservation.confirm(now);
        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                ReservationStatus.HOLDING,
                ReservationStatus.CONFIRMED,
                ReservationStatusChangeReason.USER_CONFIRMED
        ));
        return ReservationCommandResult.completed(reservation);
    }

    @Transactional
    public ReservationCommandResult cancel(UUID reservationId, String authenticatedUserId) {
        Reservation reservation = getReservation(reservationId);
        validateOwner(reservation, authenticatedUserId);
        Instant now = clock.instant();

        if (reservation.getStatus() != ReservationStatus.HOLDING) {
            throw new ReservationConflictException("Only HOLDING reservations can be cancelled.");
        }

        reservation.cancel(now);
        statusHistoryRepository.save(new ReservationStatusHistory(
                reservation.getReservationId(),
                ReservationStatus.HOLDING,
                ReservationStatus.CANCELLED,
                ReservationStatusChangeReason.USER_CANCELLED
        ));
        return ReservationCommandResult.completed(reservation);
    }

    @Transactional
    public int expireHoldingReservations() {
        Instant now = clock.instant();
        List<Reservation> reservations = reservationRepository
                .findTop100ByStatusAndHoldExpiresAtBeforeOrderByHoldExpiresAtAsc(ReservationStatus.HOLDING, now);

        for (Reservation reservation : reservations) {
            reservation.expire(now);
            statusHistoryRepository.save(new ReservationStatusHistory(
                    reservation.getReservationId(),
                    ReservationStatus.HOLDING,
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
}

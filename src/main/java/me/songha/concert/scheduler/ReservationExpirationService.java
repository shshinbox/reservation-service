package me.songha.concert.scheduler;

import lombok.RequiredArgsConstructor;
import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeatStatus;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.domain.ReservationStatusChangeReason;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import me.songha.concert.reservation.service.ReservationPaymentPort;
import me.songha.concert.time.AppTimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationExpirationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ReservationStatusHistoryRepository statusHistoryRepository;
    private final ReservationPaymentPort reservationPaymentPort;

    private final AppTimeProvider appTimeProvider;

    @Transactional
    public int expireHoldingReservations() {
        Instant now = appTimeProvider.nowInstant();
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
                ReservationSeatStatus.HOLD,
                ReservationSeatStatus.EXPIRED,
                now
        );
        reservationPaymentPort.expirePayments(reservationIds, now);
        reservationRepository.expireByReservationIds(
                reservationIds,
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatus.EXPIRED,
                now
        );

        return historyCount;
    }

}

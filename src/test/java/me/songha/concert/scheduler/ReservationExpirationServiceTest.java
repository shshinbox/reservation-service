package me.songha.concert.scheduler;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeatStatus;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import me.songha.concert.reservation.service.ReservationPaymentPort;
import me.songha.concert.time.AppTimeProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationExpirationServiceTest {

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ReservationSeatRepository reservationSeatRepository = mock(ReservationSeatRepository.class);
    private final ReservationStatusHistoryRepository statusHistoryRepository =
            mock(ReservationStatusHistoryRepository.class);
    private final ReservationPaymentPort reservationPaymentPort = mock(ReservationPaymentPort.class);
    private final AppTimeProvider appTimeProvider = mock(AppTimeProvider.class);
    private final ReservationExpirationService service = new ReservationExpirationService(
            reservationRepository,
            reservationSeatRepository,
            statusHistoryRepository,
            reservationPaymentPort,
            appTimeProvider
    );

    @Test
    void expireHoldingReservationsUsesBulkInsertAndUpdate() {
        Reservation first = paymentPendingReservation("confirmation-1");
        Reservation second = paymentPendingReservation("confirmation-2");
        List<UUID> reservationIds = List.of(first.getReservationId(), second.getReservationId());
        when(appTimeProvider.nowInstant()).thenReturn(now());
        when(reservationRepository.findTop100ByStatusAndPaymentExpiresAtBeforeOrderByPaymentExpiresAtAsc(
                ReservationStatus.PAYMENT_PENDING,
                now()
        )).thenReturn(List.of(first, second));
        when(statusHistoryRepository.insertExpirationHistories(
                reservationIds,
                ReservationStatus.PAYMENT_PENDING.name(),
                ReservationStatus.EXPIRED.name(),
                "HOLD_EXPIRED",
                now()
        )).thenReturn(2);

        int expiredCount = service.expireHoldingReservations();

        assertThat(expiredCount).isEqualTo(2);
        verify(statusHistoryRepository).insertExpirationHistories(
                reservationIds,
                ReservationStatus.PAYMENT_PENDING.name(),
                ReservationStatus.EXPIRED.name(),
                "HOLD_EXPIRED",
                now()
        );
        verify(reservationSeatRepository).expireByReservationIds(
                reservationIds,
                ReservationSeatStatus.HOLD,
                ReservationSeatStatus.EXPIRED,
                now()
        );
        verify(reservationPaymentPort).expirePayments(reservationIds, now());
        verify(reservationRepository).expireByReservationIds(
                reservationIds,
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatus.EXPIRED,
                now()
        );
        verify(reservationSeatRepository, never()).findByReservationIdOrderBySeatIdAsc(any());
        verify(statusHistoryRepository, never()).save(any());
    }

    private Reservation paymentPendingReservation(String confirmationId) {
        return Reservation.paymentPending(
                confirmationId,
                "schedule-1",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z"),
                now()
        );
    }

    private Instant now() {
        return Instant.parse("2026-05-25T11:50:00Z");
    }
}

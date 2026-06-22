package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeat;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.exception.ReservationAccessDeniedException;
import me.songha.concert.reservation.exception.ReservationConflictException;
import me.songha.concert.reservation.redis.SoldSeatRedisRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import me.songha.concert.reservation.service.ReservationOperationService;
import me.songha.concert.reservation.service.dto.ReservationOperationResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationOperationServiceTest {

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ReservationSeatRepository reservationSeatRepository = mock(ReservationSeatRepository.class);
    private final ReservationStatusHistoryRepository statusHistoryRepository =
            mock(ReservationStatusHistoryRepository.class);
    private final SoldSeatRedisRepository soldSeatRedisRepository = mock(SoldSeatRedisRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T11:50:00Z"), ZoneOffset.UTC);
    private final ReservationOperationService service = new ReservationOperationService(
            reservationRepository,
            reservationSeatRepository,
            statusHistoryRepository,
            soldSeatRedisRepository,
            clock
    );

    @Test
    void confirmPaidCompletesPaymentPendingReservationBeforeExpiration() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        List<ReservationSeat> seats = seats(reservation);
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatRepository.findByReservationIdOrderBySeatIdAsc(reservation.getReservationId()))
                .thenReturn(seats);

        ReservationOperationResult result = service.confirmPaid(reservationId);

        assertThat(result.completed()).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(seats).extracting(ReservationSeat::getStatus)
                .containsOnly(ReservationStatus.CONFIRMED);
        verify(soldSeatRedisRepository).markSold(reservation, seats);
        verify(statusHistoryRepository).save(any());
    }

    @Test
    void confirmPaidExpiresReservationWhenPaymentIsExpired() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.paymentPending(
                "confirmation-1",
                "schedule-1",
                "user-1",
                Instant.parse("2026-05-25T11:45:00Z")
        );
        List<ReservationSeat> seats = seats(reservation);
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatRepository.findByReservationIdOrderBySeatIdAsc(reservation.getReservationId()))
                .thenReturn(seats);

        ReservationOperationResult result = service.confirmPaid(reservationId);

        assertThat(result.completed()).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(seats).extracting(ReservationSeat::getStatus)
                .containsOnly(ReservationStatus.EXPIRED);
        verify(statusHistoryRepository).save(any());
    }

    @Test
    void cancelPaymentPendingReservationCancelsSeats() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        List<ReservationSeat> seats = seats(reservation);
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatRepository.findByReservationIdOrderBySeatIdAsc(reservation.getReservationId()))
                .thenReturn(seats);

        ReservationOperationResult result = service.cancel(reservationId, "user-1");

        assertThat(result.completed()).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(seats).extracting(ReservationSeat::getStatus)
                .containsOnly(ReservationStatus.CANCELLED);
        verify(soldSeatRedisRepository, never()).deleteSold(reservation, seats);
        verify(statusHistoryRepository).save(any());
    }

    @Test
    void cancelThrowsWhenReservationIsConfirmed() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        List<ReservationSeat> seats = seats(reservation);
        reservation.confirm(Instant.parse("2026-05-25T11:49:00Z"));
        seats.forEach(ReservationSeat::confirm);
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatRepository.findByReservationIdOrderBySeatIdAsc(reservation.getReservationId()))
                .thenReturn(seats);

        assertThatThrownBy(() -> service.cancel(reservationId, "user-1"))
                .isInstanceOf(ReservationConflictException.class);
    }

    @Test
    void cancelThrowsWhenAuthenticatedUserIsNotOwner() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatRepository.findByReservationIdOrderBySeatIdAsc(reservation.getReservationId()))
                .thenReturn(seats(reservation));

        assertThatThrownBy(() -> service.cancel(reservationId, "user-2"))
                .isInstanceOf(ReservationAccessDeniedException.class);
    }

    @Test
    void expireHoldingReservationsUsesBulkInsertAndUpdate() {
        Reservation first = paymentPendingReservation("confirmation-1");
        Reservation second = paymentPendingReservation("confirmation-2");
        List<UUID> reservationIds = List.of(first.getReservationId(), second.getReservationId());
        when(reservationRepository.findTop100ByStatusAndPaymentExpiresAtBeforeOrderByPaymentExpiresAtAsc(
                ReservationStatus.PAYMENT_PENDING,
                clock.instant()
        )).thenReturn(List.of(first, second));
        when(statusHistoryRepository.insertExpirationHistories(
                reservationIds,
                ReservationStatus.PAYMENT_PENDING.name(),
                ReservationStatus.EXPIRED.name(),
                "HOLD_EXPIRED",
                clock.instant()
        )).thenReturn(2);

        int expiredCount = service.expireHoldingReservations();

        assertThat(expiredCount).isEqualTo(2);
        verify(statusHistoryRepository).insertExpirationHistories(
                reservationIds,
                ReservationStatus.PAYMENT_PENDING.name(),
                ReservationStatus.EXPIRED.name(),
                "HOLD_EXPIRED",
                clock.instant()
        );
        verify(reservationSeatRepository).expireByReservationIds(
                reservationIds,
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatus.EXPIRED,
                clock.instant()
        );
        verify(reservationRepository).expireByReservationIds(
                reservationIds,
                ReservationStatus.PAYMENT_PENDING,
                ReservationStatus.EXPIRED,
                clock.instant()
        );
        verify(reservationSeatRepository, never()).findByReservationIdOrderBySeatIdAsc(any());
        verify(statusHistoryRepository, never()).save(any());
    }

    private Reservation paymentPendingReservation() {
        return paymentPendingReservation("confirmation-1");
    }

    private Reservation paymentPendingReservation(String confirmationId) {
        return Reservation.paymentPending(
                confirmationId,
                "schedule-1",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
    }

    private List<ReservationSeat> seats(Reservation reservation) {
        return reservation.createSeats(List.of("A-12"));
    }
}

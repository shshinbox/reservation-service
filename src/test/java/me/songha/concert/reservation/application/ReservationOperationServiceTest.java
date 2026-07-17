package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeat;
import me.songha.concert.reservation.domain.ReservationSeatStatus;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.dto.ReservationOperationResult;
import me.songha.concert.reservation.exception.ReservationAccessDeniedException;
import me.songha.concert.reservation.exception.ReservationConflictException;
import me.songha.concert.reservation.redis.SoldSeatRedisRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import me.songha.concert.reservation.service.ReservationOperationService;
import me.songha.concert.reservation.service.ReservationPaymentPort;
import me.songha.concert.reservation.service.ReservationSeatService;
import me.songha.concert.time.AppTimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationOperationServiceTest {

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ReservationSeatService reservationSeatService = mock(ReservationSeatService.class);
    private final ReservationSeatRepository reservationSeatRepository = mock(ReservationSeatRepository.class);
    private final ReservationStatusHistoryRepository statusHistoryRepository =
            mock(ReservationStatusHistoryRepository.class);
    private final SoldSeatRedisRepository soldSeatRedisRepository = mock(SoldSeatRedisRepository.class);
    private final ReservationPaymentPort reservationPaymentPort = mock(ReservationPaymentPort.class);
    private final AppTimeProvider appTimeProvider = mock(AppTimeProvider.class);
    private final ReservationOperationService service = new ReservationOperationService(
            reservationRepository,
            reservationSeatService,
            reservationSeatRepository,
            statusHistoryRepository,
            soldSeatRedisRepository,
            reservationPaymentPort,
            appTimeProvider
    );

    @Test
    void confirmPaidCompletesPaymentPendingReservationBeforeExpiration() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        List<ReservationSeat> seats = seats(reservation);
        when(appTimeProvider.nowInstant()).thenReturn(now());
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatService.getSeats(reservation)).thenReturn(seats);

        ReservationOperationResult result = service.confirm(reservationId);

        assertThat(result.completed()).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(seats).extracting(ReservationSeat::getStatus)
                .containsOnly(ReservationSeatStatus.RESERVED);
        verify(soldSeatRedisRepository).markSold(reservation, seats);
        verify(statusHistoryRepository).save(any());
    }

    @Test
    void confirmPaidDeletesRedisSoldWhenTransactionRollsBackAfterMarkSold() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        List<ReservationSeat> seats = seats(reservation);
        when(appTimeProvider.nowInstant()).thenReturn(now());
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatService.getSeats(reservation)).thenReturn(seats);

        TransactionSynchronizationManager.initSynchronization();
        try {
            ReservationOperationResult result = service.confirm(reservationId);

            assertThat(result.completed()).isTrue();
            verify(soldSeatRedisRepository).markSold(reservation, seats);
            verify(soldSeatRedisRepository, never()).deleteSold(reservation, seats);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization ->
                            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(soldSeatRedisRepository).deleteSold(reservation, seats);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void confirmPaidRetriesRedisSoldMarkFailure() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        List<ReservationSeat> seats = seats(reservation);
        RuntimeException redisFailure = new RuntimeException("redis unavailable");
        when(appTimeProvider.nowInstant()).thenReturn(now());
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatService.getSeats(reservation)).thenReturn(seats);
        doThrow(redisFailure)
                .doNothing()
                .when(soldSeatRedisRepository).markSold(reservation, seats);

        ReservationOperationResult result = service.confirm(reservationId);

        assertThat(result.completed()).isTrue();
        verify(soldSeatRedisRepository, times(2)).markSold(reservation, seats);
        verify(soldSeatRedisRepository, never()).syncScheduleSold(any(), any());
    }

    @Test
    void confirmPaidSyncsScheduleSoldWhenRedisSoldMarkRetriesFail() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        List<ReservationSeat> seats = seats(reservation);
        RuntimeException redisFailure = new RuntimeException("redis unavailable");
        when(appTimeProvider.nowInstant()).thenReturn(now());
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatService.getSeats(reservation)).thenReturn(seats);
        when(reservationSeatRepository.findByScheduleIdAndStatusOrderBySeatIdAsc(
                reservation.getScheduleId(),
                ReservationSeatStatus.RESERVED
        )).thenReturn(seats);
        doThrow(redisFailure).when(soldSeatRedisRepository).markSold(reservation, seats);

        ReservationOperationResult result = service.confirm(reservationId);

        assertThat(result.completed()).isTrue();
        verify(soldSeatRedisRepository, times(4)).markSold(reservation, seats);
        verify(soldSeatRedisRepository).syncScheduleSold(reservation.getScheduleId(), seats);
    }

    @Test
    void confirmPaidThrowsWhenRedisSoldMarkAndScheduleSyncFail() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        List<ReservationSeat> seats = seats(reservation);
        RuntimeException redisFailure = new RuntimeException("redis unavailable");
        RuntimeException syncFailure = new RuntimeException("sync unavailable");
        when(appTimeProvider.nowInstant()).thenReturn(now());
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatService.getSeats(reservation)).thenReturn(seats);
        when(reservationSeatRepository.findByScheduleIdAndStatusOrderBySeatIdAsc(
                reservation.getScheduleId(),
                ReservationSeatStatus.RESERVED
        )).thenReturn(seats);
        doThrow(redisFailure).when(soldSeatRedisRepository).markSold(reservation, seats);
        doThrow(syncFailure).when(soldSeatRedisRepository).syncScheduleSold(reservation.getScheduleId(), seats);

        assertThatThrownBy(() -> service.confirm(reservationId))
                .isInstanceOf(ReservationConflictException.class)
                .hasMessage("Failed to mark sold seats in Redis.")
                .hasCause(syncFailure);
        assertThat(syncFailure.getSuppressed()).contains(redisFailure);
        verify(soldSeatRedisRepository, times(4)).markSold(reservation, seats);
    }

    @Test
    void confirmPaidExpiresReservationWhenPaymentIsExpired() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.paymentPending(
                "confirmation-1",
                "schedule-1",
                "user-1",
                Instant.parse("2026-05-25T11:45:00Z"),
                now()
        );
        List<ReservationSeat> seats = seats(reservation);
        when(appTimeProvider.nowInstant()).thenReturn(now());
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatService.getSeats(reservation)).thenReturn(seats);

        ReservationOperationResult result = service.confirm(reservationId);

        assertThat(result.completed()).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(seats).extracting(ReservationSeat::getStatus)
                .containsOnly(ReservationSeatStatus.EXPIRED);
        verify(reservationPaymentPort, never()).expirePayments(any(), any());
        verify(statusHistoryRepository).save(any());
    }

    @Test
    void cancelPaymentPendingReservationCancelsSeats() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        List<ReservationSeat> seats = seats(reservation);
        when(appTimeProvider.nowInstant()).thenReturn(now());
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatService.getSeats(reservation)).thenReturn(seats);

        ReservationOperationResult result = service.cancel(reservationId, "user-1");

        assertThat(result.completed()).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(seats).extracting(ReservationSeat::getStatus)
                .containsOnly(ReservationSeatStatus.CANCELLED);
        verify(reservationPaymentPort).cancelPayment(reservation.getReservationId(), now());
        verify(statusHistoryRepository).save(any());
        verify(soldSeatRedisRepository).deleteHold(reservation, seats);
    }

    @Test
    void cancelDeletesRedisHoldWhenReservationIsAlreadyCancelled() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        List<ReservationSeat> seats = seats(reservation);
        reservation.cancel(now());
        seats.forEach(seat -> seat.cancel(now()));
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatService.getSeats(reservation)).thenReturn(seats);

        ReservationOperationResult result = service.cancel(reservationId, "user-1");

        assertThat(result.completed()).isTrue();
        verify(soldSeatRedisRepository).deleteHold(reservation, seats);
    }

    @Test
    void cancelThrowsWhenReservationIsConfirmed() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        List<ReservationSeat> seats = seats(reservation);
        reservation.confirm(Instant.parse("2026-05-25T11:49:00Z"));
        seats.forEach(seat -> seat.confirm(Instant.parse("2026-05-25T11:49:00Z")));
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationSeatService.getSeats(reservation)).thenReturn(seats);

        assertThatThrownBy(() -> service.cancel(reservationId, "user-1"))
                .isInstanceOf(ReservationConflictException.class);
    }

    @Test
    void cancelThrowsWhenAuthenticatedUserIsNotOwner() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = paymentPendingReservation();
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.cancel(reservationId, "user-2"))
                .isInstanceOf(ReservationAccessDeniedException.class);
    }

    private Reservation paymentPendingReservation() {
        return paymentPendingReservation("confirmation-1");
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

    private List<ReservationSeat> seats(Reservation reservation) {
        return reservation.createSeats(List.of("A-12"), now());
    }

    private Instant now() {
        return Instant.parse("2026-05-25T11:50:00Z");
    }
}

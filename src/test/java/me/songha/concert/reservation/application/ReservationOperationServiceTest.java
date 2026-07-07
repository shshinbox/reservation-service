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
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import me.songha.concert.reservation.service.ReservationOperationService;
import me.songha.concert.reservation.service.ReservationSeatService;
import me.songha.concert.time.AppTimeProvider;
import org.junit.jupiter.api.Test;

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
    private final ReservationStatusHistoryRepository statusHistoryRepository =
            mock(ReservationStatusHistoryRepository.class);
    private final SoldSeatRedisRepository soldSeatRedisRepository = mock(SoldSeatRedisRepository.class);
    private final AppTimeProvider appTimeProvider = mock(AppTimeProvider.class);
    private final ReservationOperationService service = new ReservationOperationService(
            reservationRepository,
            reservationSeatService,
            statusHistoryRepository,
            soldSeatRedisRepository,
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
        verify(statusHistoryRepository).save(any());
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

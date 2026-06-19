package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.domain.SoldSeat;
import me.songha.concert.reservation.infrastructure.redis.SoldSeatRedisRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import me.songha.concert.reservation.repository.SoldSeatRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationCommandServiceTest {

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ReservationStatusHistoryRepository statusHistoryRepository =
            mock(ReservationStatusHistoryRepository.class);
    private final SoldSeatRepository soldSeatRepository = mock(SoldSeatRepository.class);
    private final SoldSeatRedisRepository soldSeatRedisRepository = mock(SoldSeatRedisRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T11:50:00Z"), ZoneOffset.UTC);
    private final ReservationCommandService service = new ReservationCommandService(
            reservationRepository,
            statusHistoryRepository,
            soldSeatRepository,
            soldSeatRedisRepository,
            clock
    );

    @Test
    void confirmPaidCompletesPaymentPendingReservationBeforeExpiration() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.paymentPending(
                UUID.randomUUID(),
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        ReservationCommandResult result = service.confirmPaid(reservationId);

        assertThat(result.completed()).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        ArgumentCaptor<SoldSeat> soldSeatCaptor = ArgumentCaptor.forClass(SoldSeat.class);
        verify(soldSeatRepository).save(soldSeatCaptor.capture());
        assertThat(soldSeatCaptor.getValue().getReservationId()).isEqualTo(reservation.getReservationId());
        assertThat(soldSeatCaptor.getValue().getScheduleId()).isEqualTo("schedule-1");
        assertThat(soldSeatCaptor.getValue().getSeatId()).isEqualTo("A-12");
        assertThat(soldSeatCaptor.getValue().getUserId()).isEqualTo("user-1");
        verify(soldSeatRedisRepository).markSold(reservation);
        verify(statusHistoryRepository).save(any());
    }

    @Test
    void confirmPaidExpiresReservationWhenHoldIsExpired() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.paymentPending(
                UUID.randomUUID(),
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:45:00Z")
        );
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        ReservationCommandResult result = service.confirmPaid(reservationId);

        assertThat(result.completed()).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        verify(statusHistoryRepository).save(any());
    }

    @Test
    void cancelConfirmedReservationCancelsSoldSeatAndDeletesRedisSoldKey() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.paymentPending(
                UUID.randomUUID(),
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
        reservation.confirm(Instant.parse("2026-05-25T11:49:00Z"));
        SoldSeat soldSeat = SoldSeat.sold(reservation, Instant.parse("2026-05-25T11:49:00Z"));
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(soldSeatRepository.findByReservationIdAndCancelledAtIsNull(reservation.getReservationId()))
                .thenReturn(Optional.of(soldSeat));

        ReservationCommandResult result = service.cancel(reservationId, "user-1");

        assertThat(result.completed()).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(soldSeat.getCancelledAt()).isEqualTo(Instant.parse("2026-05-25T11:50:00Z"));
        verify(soldSeatRedisRepository).deleteSold(reservation);
        verify(statusHistoryRepository).save(any());
    }

    @Test
    void cancelCancelledReservationIsIdempotent() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.paymentPending(
                UUID.randomUUID(),
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
        reservation.cancel(Instant.parse("2026-05-25T11:49:00Z"));
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        ReservationCommandResult result = service.cancel(reservationId, "user-1");

        assertThat(result.completed()).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelThrowsWhenAuthenticatedUserIsNotOwner() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.paymentPending(
                UUID.randomUUID(),
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.cancel(reservationId, "user-2"))
                .isInstanceOf(ReservationAccessDeniedException.class);
    }
}

package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationCommandServiceTest {

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ReservationStatusHistoryRepository statusHistoryRepository =
            mock(ReservationStatusHistoryRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T11:50:00Z"), ZoneOffset.UTC);
    private final ReservationCommandService service = new ReservationCommandService(
            reservationRepository,
            statusHistoryRepository,
            clock
    );

    @Test
    void confirmCompletesHoldingReservationBeforeExpiration() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.holding(
                UUID.randomUUID(),
                "schedule-1",
                "venue-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        ReservationCommandResult result = service.confirm(reservationId, "user-1");

        assertThat(result.completed()).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(statusHistoryRepository).save(any());
    }

    @Test
    void confirmExpiresReservationWhenHoldIsExpired() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.holding(
                UUID.randomUUID(),
                "schedule-1",
                "venue-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:45:00Z")
        );
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        ReservationCommandResult result = service.confirm(reservationId, "user-1");

        assertThat(result.completed()).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        verify(statusHistoryRepository).save(any());
    }

    @Test
    void confirmThrowsWhenAuthenticatedUserIsNotOwner() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.holding(
                UUID.randomUUID(),
                "schedule-1",
                "venue-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.confirm(reservationId, "user-2"))
                .isInstanceOf(ReservationAccessDeniedException.class);
    }
}

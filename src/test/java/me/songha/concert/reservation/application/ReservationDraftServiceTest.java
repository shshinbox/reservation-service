package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.ProcessedKafkaEvent;
import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.domain.ReservationStatusHistory;
import me.songha.concert.reservation.event.KafkaEventMetadata;
import me.songha.concert.reservation.event.SeatHoldEvent;
import me.songha.concert.reservation.repository.ProcessedKafkaEventRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationDraftServiceTest {

    private final ProcessedKafkaEventRepository processedKafkaEventRepository =
            mock(ProcessedKafkaEventRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ReservationStatusHistoryRepository statusHistoryRepository =
            mock(ReservationStatusHistoryRepository.class);
    private final ReservationDraftService service = new ReservationDraftService(
            processedKafkaEventRepository,
            reservationRepository,
            statusHistoryRepository
    );

    @Test
    void heldEventCreatesPaymentPendingReservation() {
        UUID holdId = UUID.randomUUID();
        SeatHoldEvent event = heldEvent(holdId);
        when(reservationRepository.existsByHoldId(holdId)).thenReturn(false);

        service.applySeatHoldEvent(event, metadata());

        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).saveAndFlush(reservationCaptor.capture());
        Reservation reservation = reservationCaptor.getValue();
        assertThat(reservation.getHoldId()).isEqualTo(holdId);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
        assertThat(reservation.getScheduleId()).isEqualTo("schedule-1");
        assertThat(reservation.getSeatId()).isEqualTo("A-12");
        assertThat(reservation.getUserId()).isEqualTo("user-1");
        assertThat(reservation.getHoldExpiresAt()).isEqualTo(Instant.parse("2026-05-25T11:55:00Z"));
        verify(processedKafkaEventRepository).saveAndFlush(any(ProcessedKafkaEvent.class));
        verify(statusHistoryRepository).save(any(ReservationStatusHistory.class));
    }

    @Test
    void releasedEventCancelsPaymentPendingReservation() {
        UUID holdId = UUID.randomUUID();
        Reservation reservation = Reservation.paymentPending(
                holdId,
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
        when(reservationRepository.findByHoldIdForUpdate(holdId)).thenReturn(Optional.of(reservation));

        service.applySeatHoldEvent(releasedEvent(holdId), metadata());

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getCancelledAt()).isEqualTo(Instant.parse("2026-05-25T11:51:00Z"));
        verify(statusHistoryRepository).save(any(ReservationStatusHistory.class));
    }

    @Test
    void releasedEventIgnoresConfirmedReservation() {
        UUID holdId = UUID.randomUUID();
        Reservation reservation = Reservation.paymentPending(
                holdId,
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
        reservation.confirm(Instant.parse("2026-05-25T11:50:00Z"));
        when(reservationRepository.findByHoldIdForUpdate(holdId)).thenReturn(Optional.of(reservation));

        service.applySeatHoldEvent(releasedEvent(holdId), metadata());

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(statusHistoryRepository, never()).save(any(ReservationStatusHistory.class));
    }

    private SeatHoldEvent heldEvent(UUID holdId) {
        return new SeatHoldEvent(
                UUID.randomUUID(),
                "SEAT_HOLD_HELD",
                holdId,
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z"),
                Instant.parse("2026-05-25T11:50:00Z")
        );
    }

    private SeatHoldEvent releasedEvent(UUID holdId) {
        return new SeatHoldEvent(
                UUID.randomUUID(),
                "SEAT_HOLD_RELEASED",
                holdId,
                "schedule-1",
                "A-12",
                "user-1",
                null,
                Instant.parse("2026-05-25T11:51:00Z")
        );
    }

    private KafkaEventMetadata metadata() {
        return new KafkaEventMetadata("seat-hold-events", 0, 1L);
    }
}

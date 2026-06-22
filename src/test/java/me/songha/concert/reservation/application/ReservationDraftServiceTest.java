package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.ProcessedKafkaEvent;
import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeat;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.domain.ReservationStatusHistory;
import me.songha.concert.reservation.kafka.KafkaEventMetadata;
import me.songha.concert.reservation.kafka.SeatHoldEvent;
import me.songha.concert.reservation.kafka.SeatHoldEventConflictException;
import me.songha.concert.reservation.repository.ProcessedKafkaEventRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import me.songha.concert.reservation.service.ReservationDraftService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationDraftServiceTest {

    private final ProcessedKafkaEventRepository processedKafkaEventRepository =
            mock(ProcessedKafkaEventRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ReservationSeatRepository reservationSeatRepository = mock(ReservationSeatRepository.class);
    private final ReservationStatusHistoryRepository statusHistoryRepository =
            mock(ReservationStatusHistoryRepository.class);
    private final ReservationDraftService service = new ReservationDraftService(
            processedKafkaEventRepository,
            reservationRepository,
            reservationSeatRepository,
            statusHistoryRepository
    );

    @Test
    void confirmedEventCreatesPaymentPendingReservationAndSeats() {
        SeatHoldEvent event = confirmedEvent();
        when(reservationRepository.existsByConfirmationId("confirmation-1")).thenReturn(false);

        service.applySeatHoldEvent(event, metadata());

        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        Reservation reservation = reservationCaptor.getValue();
        assertThat(reservation.getConfirmationId()).isEqualTo("confirmation-1");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
        assertThat(reservation.getScheduleId()).isEqualTo("schedule-1");
        assertThat(reservation.getUserId()).isEqualTo("user-1");
        assertThat(reservation.getPaymentExpiresAt()).isEqualTo(Instant.parse("2026-05-28T11:50:00Z"));

        ArgumentCaptor<List<ReservationSeat>> seatsCaptor = ArgumentCaptor.captor();
        verify(reservationSeatRepository).saveAll(seatsCaptor.capture());
        assertThat(seatsCaptor.getValue())
                .extracting(ReservationSeat::getSeatId)
                .containsExactly("A-12", "A-13");
        verify(processedKafkaEventRepository).save(any(ProcessedKafkaEvent.class));
        verify(statusHistoryRepository).save(any(ReservationStatusHistory.class));
    }

    @Test
    void confirmedEventSkipsInsertWhenEventAlreadyProcessed() {
        SeatHoldEvent event = confirmedEvent();
        when(processedKafkaEventRepository.existsById("event-1")).thenReturn(true);

        service.applySeatHoldEvent(event, metadata());

        verify(processedKafkaEventRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        verify(reservationSeatRepository, never()).saveAll(any());
    }

    @Test
    void confirmedEventThrowsWhenActiveSeatExists() {
        SeatHoldEvent event = confirmedEvent();
        when(reservationSeatRepository.existsByScheduleIdAndSeatIdAndStatusIn(any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.applySeatHoldEvent(event, metadata()))
                .isInstanceOf(SeatHoldEventConflictException.class)
                .hasMessageContaining("Active reservation seat already exists");

        verify(reservationSeatRepository).existsByScheduleIdAndSeatIdAndStatusIn(
                eq("schedule-1"),
                eq("A-12"),
                any()
        );
        verify(reservationRepository, never()).save(any());
        verify(reservationSeatRepository, never()).saveAll(any());
    }

    @Test
    void confirmedEventRejectsDuplicatedSeatIdsAfterTrim() {
        SeatHoldEvent event = new SeatHoldEvent(
                "event-1",
                "SEAT_HOLD_CONFIRMED",
                "confirmation-1",
                "schedule-1",
                List.of("A-12", " A-12 "),
                "user-1",
                null,
                Instant.parse("2026-05-25T11:50:00Z"),
                2
        );

        assertThatThrownBy(() -> service.applySeatHoldEvent(event, metadata()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicated");
    }

    private SeatHoldEvent confirmedEvent() {
        return new SeatHoldEvent(
                "event-1",
                "SEAT_HOLD_CONFIRMED",
                "confirmation-1",
                "schedule-1",
                List.of("A-12", "A-13"),
                "user-1",
                null,
                Instant.parse("2026-05-25T11:50:00Z"),
                2
        );
    }

    private KafkaEventMetadata metadata() {
        return new KafkaEventMetadata("seat-hold-events", 0, 1L);
    }
}

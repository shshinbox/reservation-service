package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.ProcessedKafkaEvent;
import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeat;
import me.songha.concert.reservation.domain.ReservationSeatStatus;
import me.songha.concert.reservation.domain.ReservationStatus;
import me.songha.concert.reservation.domain.ReservationStatusHistory;
import me.songha.concert.consumer.KafkaEventMetadata;
import me.songha.concert.consumer.SeatHoldEvent;
import me.songha.concert.consumer.SeatHoldEventConflictException;
import me.songha.concert.reservation.dto.CreateReservationDraftCommand;
import me.songha.concert.reservation.repository.ProcessedKafkaEventRepository;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import me.songha.concert.reservation.repository.ReservationStatusHistoryRepository;
import me.songha.concert.reservation.service.ReservationCreationService;
import me.songha.concert.reservation.service.ReservationPaymentPort;
import me.songha.concert.schedule.ScheduleClient;
import me.songha.concert.schedule.ScheduleInfo;
import me.songha.concert.time.AppTimeProvider;
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

class ReservationCreationServiceTest {

    private final ProcessedKafkaEventRepository processedKafkaEventRepository =
            mock(ProcessedKafkaEventRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ReservationSeatRepository reservationSeatRepository = mock(ReservationSeatRepository.class);
    private final ReservationStatusHistoryRepository statusHistoryRepository =
            mock(ReservationStatusHistoryRepository.class);
    private final ReservationPaymentPort reservationPaymentPort = mock(ReservationPaymentPort.class);
    private final ScheduleClient scheduleClient = mock(ScheduleClient.class);
    private final AppTimeProvider appTimeProvider = mock(AppTimeProvider.class);
    private final ReservationCreationService service = new ReservationCreationService(
            processedKafkaEventRepository,
            reservationRepository,
            reservationSeatRepository,
            statusHistoryRepository,
            reservationPaymentPort,
            scheduleClient,
            appTimeProvider
    );

    @Test
    void confirmedEventCreatesPaymentPendingReservationAndSeats() {
        CreateReservationDraftCommand command = confirmedCommand();
        when(appTimeProvider.nowInstant()).thenReturn(Instant.parse("2026-05-25T11:50:00Z"));
        when(scheduleClient.getSchedule("schedule-1")).thenReturn(reservableSchedule());
        when(reservationRepository.existsByConfirmationId("confirmation-1")).thenReturn(false);

        service.createPending(command);

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
        assertThat(seatsCaptor.getValue())
                .extracting(ReservationSeat::getStatus)
                .containsOnly(ReservationSeatStatus.HOLD);
        verify(reservationPaymentPort).createReadyPayment(
                reservation.getReservationId(),
                Instant.parse("2026-05-25T11:50:00Z")
        );
        verify(processedKafkaEventRepository).save(any(ProcessedKafkaEvent.class));
        verify(statusHistoryRepository).save(any(ReservationStatusHistory.class));
    }

    @Test
    void confirmedEventSkipsInsertWhenEventAlreadyProcessed() {
        CreateReservationDraftCommand command = confirmedCommand();
        when(appTimeProvider.nowInstant()).thenReturn(Instant.parse("2026-05-25T11:50:00Z"));
        when(scheduleClient.getSchedule("schedule-1")).thenReturn(reservableSchedule());
        when(processedKafkaEventRepository.existsById("event-1")).thenReturn(true);

        service.createPending(command);

        verify(processedKafkaEventRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        verify(reservationSeatRepository, never()).saveAll(any());
        verify(reservationPaymentPort, never()).createReadyPayment(any(), any());
    }

    @Test
    void confirmedEventThrowsWhenActiveSeatExists() {
        CreateReservationDraftCommand command = confirmedCommand();
        when(appTimeProvider.nowInstant()).thenReturn(Instant.parse("2026-05-25T11:50:00Z"));
        when(scheduleClient.getSchedule("schedule-1")).thenReturn(reservableSchedule());
        when(reservationSeatRepository.existsByScheduleIdAndSeatIdAndStatusIn(any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createPending(command))
                .isInstanceOf(SeatHoldEventConflictException.class)
                .hasMessageContaining("Active reservation seat already exists");

        verify(reservationSeatRepository).existsByScheduleIdAndSeatIdAndStatusIn(
                eq("schedule-1"),
                eq("A-12"),
                any()
        );
        verify(reservationRepository, never()).save(any());
        verify(reservationSeatRepository, never()).saveAll(any());
        verify(reservationPaymentPort, never()).createReadyPayment(any(), any());
    }

    @Test
    void confirmedEventRejectsDuplicatedSeatIdsAfterTrim() {
        CreateReservationDraftCommand command = CreateReservationDraftCommand.from(new SeatHoldEvent(
                "event-1",
                "SEAT_HOLD_CONFIRMED",
                "confirmation-1",
                "schedule-1",
                List.of("A-12", " A-12 "),
                "user-1",
                null,
                Instant.parse("2026-05-25T11:50:00Z"),
                2
        ), metadata());

        assertThatThrownBy(() -> service.createPending(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicated");
    }

    @Test
    void confirmedEventThrowsWhenScheduleIsNotReservable() {
        CreateReservationDraftCommand command = confirmedCommand();
        when(appTimeProvider.nowInstant()).thenReturn(Instant.parse("2026-05-25T11:50:00Z"));
        when(scheduleClient.getSchedule("schedule-1")).thenReturn(new ScheduleInfo(
                "schedule-1",
                Instant.parse("2026-05-25T10:00:00Z"),
                Instant.parse("2026-05-25T12:00:00Z"),
                Instant.parse("2026-05-25T11:00:00Z")
        ));

        assertThatThrownBy(() -> service.createPending(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Schedule is not reservable.");

        verify(reservationRepository, never()).save(any());
        verify(reservationSeatRepository, never()).saveAll(any());
        verify(reservationPaymentPort, never()).createReadyPayment(any(), any());
    }

    private CreateReservationDraftCommand confirmedCommand() {
        return CreateReservationDraftCommand.from(confirmedEvent(), metadata());
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

    private ScheduleInfo reservableSchedule() {
        return new ScheduleInfo(
                "schedule-1",
                Instant.parse("2026-05-26T12:00:00Z"),
                Instant.parse("2026-05-26T14:00:00Z"),
                Instant.parse("2026-05-26T11:00:00Z")
        );
    }
}

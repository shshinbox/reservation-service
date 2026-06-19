package me.songha.concert.reservation.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    @Test
    void paymentPendingCreatesPaymentPendingReservation() {
        Reservation reservation = Reservation.paymentPending(
                UUID.randomUUID(),
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );

        assertThat(reservation.getReservationId()).isNotNull();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
    }

    @Test
    void confirmChangesStatusToConfirmed() {
        Reservation reservation = Reservation.paymentPending(
                UUID.randomUUID(),
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );

        reservation.confirm(Instant.parse("2026-05-25T11:50:00Z"));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(Instant.parse("2026-05-25T11:50:00Z"));
    }

    @Test
    void confirmThrowsWhenHoldIsExpired() {
        Reservation reservation = Reservation.paymentPending(
                UUID.randomUUID(),
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );

        assertThatThrownBy(() -> reservation.confirm(Instant.parse("2026-05-25T11:55:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }
}

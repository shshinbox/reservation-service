package me.songha.concert.reservation.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final Instant NOW = Instant.parse("2026-05-25T11:50:00Z");

    @Test
    void paymentPendingCreatesPaymentPendingReservation() {
        Reservation reservation = Reservation.paymentPending(
                "confirmation-1",
                "schedule-1",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z"),
                NOW
        );

        assertThat(reservation.getReservationId()).isNotNull();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
        assertThat(reservation.getCreatedAt()).isEqualTo(NOW);
        assertThat(reservation.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void confirmChangesStatusToConfirmed() {
        Reservation reservation = Reservation.paymentPending(
                "confirmation-1",
                "schedule-1",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z"),
                NOW
        );

        reservation.confirm(Instant.parse("2026-05-25T11:50:00Z"));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(Instant.parse("2026-05-25T11:50:00Z"));
    }

    @Test
    void confirmThrowsWhenHoldIsExpired() {
        Reservation reservation = Reservation.paymentPending(
                "confirmation-1",
                "schedule-1",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z"),
                NOW
        );

        assertThatThrownBy(() -> reservation.confirm(Instant.parse("2026-05-25T11:55:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }
}

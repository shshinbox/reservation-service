package me.songha.concert.reservation.repository;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ReservationRepository reservationRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Test
    void activeReservationCannotUseSameScheduleAndSeat() {
        Reservation first = holdingReservation("schedule-1", "A-12");
        Reservation second = holdingReservation("schedule-1", "A-12");

        reservationRepository.saveAndFlush(first);

        assertThatThrownBy(() -> reservationRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void expiredReservationAllowsSameScheduleAndSeatAgain() {
        Reservation first = holdingReservation("schedule-1", "A-12");
        reservationRepository.saveAndFlush(first);

        first.expire(Instant.parse("2026-05-25T11:56:00Z"));
        reservationRepository.saveAndFlush(first);

        Reservation second = holdingReservation("schedule-1", "A-12");
        reservationRepository.saveAndFlush(second);

        assertThat(second.getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
    }

    @Test
    void holdIdMustBeUnique() {
        UUID holdId = UUID.randomUUID();
        Reservation first = holdingReservation(holdId, "schedule-1", "A-12");
        Reservation second = holdingReservation(holdId, "schedule-2", "A-13");

        reservationRepository.saveAndFlush(first);

        assertThatThrownBy(() -> reservationRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Reservation holdingReservation(String scheduleId, String seatId) {
        return holdingReservation(UUID.randomUUID(), scheduleId, seatId);
    }

    private Reservation holdingReservation(UUID holdId, String scheduleId, String seatId) {
        return Reservation.paymentPending(
                holdId,
                scheduleId,
                seatId,
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
    }
}

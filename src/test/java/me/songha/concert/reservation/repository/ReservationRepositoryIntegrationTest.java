package me.songha.concert.reservation.repository;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeat;
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

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Test
    void activeReservationSeatCannotUseSameScheduleAndSeat() {
        Reservation first = reservation("confirmation-1");
        Reservation second = reservation("confirmation-2");
        reservationRepository.saveAndFlush(first);
        reservationRepository.saveAndFlush(second);
        reservationSeatRepository.saveAndFlush(ReservationSeat.paymentPending(first, "A-12"));

        assertThatThrownBy(() -> reservationSeatRepository.saveAndFlush(
                ReservationSeat.paymentPending(second, "A-12")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void expiredReservationSeatAllowsSameScheduleAndSeatAgain() {
        Reservation first = reservation("confirmation-1");
        Reservation second = reservation("confirmation-2");
        reservationRepository.saveAndFlush(first);
        ReservationSeat firstSeat = ReservationSeat.paymentPending(first, "A-12");
        reservationSeatRepository.saveAndFlush(firstSeat);

        first.expire(Instant.parse("2026-05-25T11:56:00Z"));
        firstSeat.expire();
        reservationRepository.saveAndFlush(first);
        reservationSeatRepository.saveAndFlush(firstSeat);

        reservationRepository.saveAndFlush(second);
        ReservationSeat secondSeat = reservationSeatRepository.saveAndFlush(
                ReservationSeat.paymentPending(second, "A-12")
        );

        assertThat(secondSeat.getStatus()).isEqualTo(ReservationStatus.PAYMENT_PENDING);
    }

    @Test
    void confirmationIdMustBeUnique() {
        Reservation first = reservation("confirmation-1");
        Reservation second = reservation("confirmation-1");

        reservationRepository.saveAndFlush(first);

        assertThatThrownBy(() -> reservationRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Reservation reservation(String confirmationId) {
        return Reservation.paymentPending(
                confirmationId,
                "schedule-1",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
    }
}

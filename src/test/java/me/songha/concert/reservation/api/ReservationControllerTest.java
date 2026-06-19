package me.songha.concert.reservation.api;

import me.songha.concert.reservation.api.auth.AuthenticatedUserArgumentResolver;
import me.songha.concert.reservation.application.ReservationCommandService;
import me.songha.concert.reservation.application.ReservationQueryService;
import me.songha.concert.reservation.config.WebConfig;
import me.songha.concert.reservation.domain.Reservation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
@Import({
        WebConfig.class,
        AuthenticatedUserArgumentResolver.class,
        ReservationExceptionHandler.class
})
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationCommandService reservationCommandService;

    @MockitoBean
    private ReservationQueryService reservationQueryService;

    @Test
    void getReservationRequiresAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/reservations/{reservationId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void getReservationUsesAuthenticatedUser() throws Exception {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.paymentPending(
                UUID.randomUUID(),
                "schedule-1",
                "A-12",
                "user-1",
                Instant.parse("2026-05-25T11:55:00Z")
        );
        when(reservationQueryService.getReservation(reservationId, "user-1")).thenReturn(reservation);

        mockMvc.perform(get("/reservations/{reservationId}", reservationId)
                        .header("X-Authenticated-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value("schedule-1"))
                .andExpect(jsonPath("$.seatId").value("A-12"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));
    }
}

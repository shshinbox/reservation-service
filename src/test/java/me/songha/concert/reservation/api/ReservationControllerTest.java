package me.songha.concert.reservation.api;

import me.songha.concert.reservation.controller.ReservationExceptionHandler;
import me.songha.concert.reservation.controller.auth.AuthenticatedUserArgumentResolver;
import me.songha.concert.reservation.controller.ReservationController;
import me.songha.concert.reservation.controller.dto.reservation.ReservationResponse;
import me.songha.concert.reservation.service.ReservationOperationService;
import me.songha.concert.reservation.service.ReservationLookupService;
import me.songha.concert.reservation.config.WebConfig;
import me.songha.concert.reservation.domain.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
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
    private ReservationOperationService reservationOperationService;

    @MockitoBean
    private ReservationLookupService reservationLookupService;

    @Test
    void getReservationRequiresAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/reservations/{reservationId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void getReservationUsesAuthenticatedUser() throws Exception {
        UUID reservationId = UUID.randomUUID();
        ReservationResponse reservation = new ReservationResponse(
                reservationId,
                "confirmation-1",
                "schedule-1",
                List.of("A-12"),
                "user-1",
                ReservationStatus.PAYMENT_PENDING,
                Instant.parse("2026-05-25T11:55:00Z"),
                null,
                null,
                null,
                Instant.parse("2026-05-25T11:50:00Z"),
                Instant.parse("2026-05-25T11:50:00Z")
        );
        when(reservationLookupService.getReservation(reservationId, "user-1")).thenReturn(reservation);

        mockMvc.perform(get("/reservations/{reservationId}", reservationId)
                        .header("X-Authenticated-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value("schedule-1"))
                .andExpect(jsonPath("$.seatIds[0]").value("A-12"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));
    }
}

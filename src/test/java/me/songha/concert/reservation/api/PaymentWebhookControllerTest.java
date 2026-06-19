package me.songha.concert.reservation.api;

import me.songha.concert.reservation.application.ReservationCommandResult;
import me.songha.concert.reservation.application.ReservationCommandService;
import me.songha.concert.reservation.domain.Reservation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentWebhookController.class)
@TestPropertySource(properties = "reservation.payment.webhook-secret=test-secret")
class PaymentWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationCommandService reservationCommandService;

    @Test
    void paidWebhookConfirmsReservation() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(reservationCommandService.confirmPaid(reservationId))
                .thenReturn(ReservationCommandResult.completed(mock(Reservation.class)));

        mockMvc.perform(post("/webhooks/payments")
                        .header("X-Payment-Webhook-Secret", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "event-1",
                                  "paymentId": "payment-1",
                                  "reservationId": "%s",
                                  "status": "PAID",
                                  "occurredAt": "2026-05-25T11:50:00Z"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true));

        verify(reservationCommandService).confirmPaid(reservationId);
    }

    @Test
    void missingSecretReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": "%s",
                                  "status": "PAID"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.processed").value(false));
    }

    @Test
    void wrongSecretReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/webhooks/payments")
                        .header("X-Payment-Webhook-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": "%s",
                                  "status": "PAID"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.processed").value(false));
    }

    @Test
    void nonPaidStatusIsIgnored() throws Exception {
        mockMvc.perform(post("/webhooks/payments")
                        .header("X-Payment-Webhook-Secret", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": "%s",
                                  "status": "FAILED"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true));

        verify(reservationCommandService, never()).confirmPaid(any());
    }

    @Test
    void expiredPaymentPendingReservationReturnsConflict() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(reservationCommandService.confirmPaid(reservationId))
                .thenReturn(ReservationCommandResult.rejected(
                        mock(Reservation.class),
                        "Reservation hold is expired."
                ));

        mockMvc.perform(post("/webhooks/payments")
                        .header("X-Payment-Webhook-Secret", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": "%s",
                                  "status": "PAID"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.processed").value(false))
                .andExpect(jsonPath("$.message").value("Reservation hold is expired."));
    }
}

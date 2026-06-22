package me.songha.concert.reservation.api;

import me.songha.concert.reservation.controller.PaymentWebhookController;
import me.songha.concert.reservation.controller.dto.reservation.ReservationResponse;
import me.songha.concert.reservation.service.dto.ReservationOperationResult;
import me.songha.concert.reservation.service.ReservationOperationService;
import me.songha.concert.reservation.domain.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
    private ReservationOperationService reservationOperationService;

    @Test
    void paidWebhookConfirmsReservation() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(reservationOperationService.confirmPaid(reservationId))
                .thenReturn(ReservationOperationResult.completed(reservationResponse(reservationId)));

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

        verify(reservationOperationService).confirmPaid(reservationId);
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

        verify(reservationOperationService, never()).confirmPaid(any());
    }

    @Test
    void expiredPaymentPendingReservationReturnsConflict() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(reservationOperationService.confirmPaid(reservationId))
                .thenReturn(ReservationOperationResult.rejected(
                        reservationResponse(reservationId),
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

    private ReservationResponse reservationResponse(UUID reservationId) {
        return new ReservationResponse(
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
    }
}

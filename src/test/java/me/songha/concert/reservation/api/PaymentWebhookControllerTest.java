package me.songha.concert.reservation.api;

import me.songha.concert.payment.controller.PaymentWebhookController;
import me.songha.concert.payment.dto.PaymentWebhookResult;
import me.songha.concert.payment.service.PaymentWebhookService;
import me.songha.concert.time.AppTimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentWebhookController.class)
class PaymentWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentWebhookService paymentWebhookService;

    @MockitoBean
    private AppTimeProvider appTimeProvider;

    @Test
    void webhookPassesRawBodyAndHeadersToService() throws Exception {
        String rawBody = "{\"paymentKey\":\"pg-payment-key-1\"}";
        when(paymentWebhookService.processWebhook(any(), eq(rawBody)))
                .thenReturn(PaymentWebhookResult.success());

        mockMvc.perform(post("/api/payments/webhooks/mock")
                        .header("X-Signature", "signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true));

        verify(paymentWebhookService).processWebhook(any(), eq(rawBody));
    }

    @Test
    void mockWebhookReturnsConflictWhenPaymentProcessingIsRejected() throws Exception {
        String rawBody = "{\"paymentKey\":\"pg-payment-key-1\"}";
        when(paymentWebhookService.processWebhook(any(), eq(rawBody)))
                .thenReturn(PaymentWebhookResult.rejected("Reservation hold is expired."));

        mockMvc.perform(post("/api/payments/webhooks/mock")
                        .header("X-Signature", "signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.processed").value(false))
                .andExpect(jsonPath("$.message").value("Reservation hold is expired."));
    }
}

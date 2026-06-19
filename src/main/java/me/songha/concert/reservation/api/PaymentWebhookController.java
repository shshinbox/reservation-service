package me.songha.concert.reservation.api;

import me.songha.concert.reservation.application.ReservationCommandResult;
import me.songha.concert.reservation.application.ReservationCommandService;
import me.songha.concert.reservation.api.payment.PaymentWebhookRequest;
import me.songha.concert.reservation.api.payment.PaymentWebhookResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/webhooks/payments")
public class PaymentWebhookController {

    private static final String WEBHOOK_SECRET_HEADER = "X-Payment-Webhook-Secret";
    private static final String PAID_STATUS = "PAID";

    private final ReservationCommandService reservationCommandService;
    @Value("${reservation.payment.webhook-secret}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<PaymentWebhookResponse> receive(
            @RequestHeader(value = WEBHOOK_SECRET_HEADER, required = false) String secret,
            @RequestBody PaymentWebhookRequest request
    ) {
        if (!webhookSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PaymentWebhookResponse(false, "Invalid webhook secret."));
        }
        if (request == null || request.reservationId() == null) {
            return ResponseEntity.badRequest()
                    .body(new PaymentWebhookResponse(false, "reservationId is required."));
        }
        if (!PAID_STATUS.equals(request.status())) {
            return ResponseEntity.ok(new PaymentWebhookResponse(true, "Ignored payment status."));
        }

        ReservationCommandResult result = reservationCommandService.confirmPaid(request.reservationId());
        if (!result.completed()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new PaymentWebhookResponse(false, result.message()));
        }
        return ResponseEntity.ok(new PaymentWebhookResponse(true, null));
    }
}

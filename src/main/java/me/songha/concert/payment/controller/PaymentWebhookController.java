package me.songha.concert.payment.controller;

import lombok.RequiredArgsConstructor;
import me.songha.concert.payment.dto.PaymentWebhookResponse;
import me.songha.concert.payment.dto.PaymentWebhookResult;
import me.songha.concert.payment.service.PaymentWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/webhooks")
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @PostMapping("/{pgProvider}")
    public ResponseEntity<PaymentWebhookResponse> receive(
            @PathVariable String pgProvider,
            @RequestHeader HttpHeaders headers,
            @RequestBody String rawBody
    ) {
        PaymentWebhookResult result = paymentWebhookService.processWebhook(headers, rawBody);
        if (!result.processed()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new PaymentWebhookResponse(false, result.message()));
        }
        return ResponseEntity.ok(new PaymentWebhookResponse(true, null));
    }
}

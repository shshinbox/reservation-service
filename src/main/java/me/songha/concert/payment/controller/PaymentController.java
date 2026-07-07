package me.songha.concert.payment.controller;

import lombok.RequiredArgsConstructor;
import me.songha.concert.auth.AuthenticatedUser;
import me.songha.concert.payment.service.PaymentService;
import me.songha.concert.payment.dto.PaymentStartRequest;
import me.songha.concert.payment.dto.PaymentStartResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public PaymentStartResponse startPayment(
            @RequestBody PaymentStartRequest request,
            AuthenticatedUser authenticatedUser
    ) {
        return paymentService.startPayment(request, authenticatedUser.userId());
    }
}

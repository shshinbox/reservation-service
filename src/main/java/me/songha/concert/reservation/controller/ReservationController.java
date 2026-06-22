package me.songha.concert.reservation.controller;

import me.songha.concert.reservation.service.ReservationOperationService;
import me.songha.concert.reservation.service.ReservationLookupService;
import me.songha.concert.reservation.service.dto.ReservationOperationResult;
import me.songha.concert.reservation.controller.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import me.songha.concert.reservation.controller.dto.reservation.ReservationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class ReservationController {

    private final ReservationOperationService reservationOperationService;
    private final ReservationLookupService reservationLookupService;

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationOperationResult cancel(
            @PathVariable UUID reservationId,
            AuthenticatedUser authenticatedUser
    ) {
        return reservationOperationService.cancel(reservationId, authenticatedUser.userId());
    }

    @GetMapping("/reservations/{reservationId}")
    public ReservationResponse getReservation(
            @PathVariable UUID reservationId,
            AuthenticatedUser authenticatedUser
    ) {
        return reservationLookupService.getReservation(reservationId, authenticatedUser.userId());
    }

    @GetMapping("/me/reservations")
    public List<ReservationResponse> getMyReservations(AuthenticatedUser authenticatedUser) {
        return reservationLookupService.getReservationsByUser(authenticatedUser.userId());
    }
}

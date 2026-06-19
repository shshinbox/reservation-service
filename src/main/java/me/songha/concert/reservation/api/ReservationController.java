package me.songha.concert.reservation.api;

import me.songha.concert.reservation.application.ReservationCommandResult;
import me.songha.concert.reservation.application.ReservationCommandService;
import me.songha.concert.reservation.application.ReservationQueryService;
import me.songha.concert.reservation.api.auth.AuthenticatedUser;
import me.songha.concert.reservation.domain.Reservation;
import lombok.RequiredArgsConstructor;
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

    private final ReservationCommandService reservationCommandService;
    private final ReservationQueryService reservationQueryService;

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationCommandResponse cancel(
            @PathVariable UUID reservationId,
            AuthenticatedUser authenticatedUser
    ) {
        return toCommandResponse(reservationCommandService.cancel(reservationId, authenticatedUser.userId()));
    }

    @GetMapping("/reservations/{reservationId}")
    public ReservationResponse getReservation(
            @PathVariable UUID reservationId,
            AuthenticatedUser authenticatedUser
    ) {
        return ReservationResponse.from(reservationQueryService.getReservation(reservationId, authenticatedUser.userId()));
    }

    @GetMapping("/me/reservations")
    public List<ReservationResponse> getMyReservations(AuthenticatedUser authenticatedUser) {
        return reservationQueryService.getReservationsByUser(authenticatedUser.userId())
                .stream()
                .map(ReservationResponse::from)
                .toList();
    }

    private ReservationCommandResponse toCommandResponse(ReservationCommandResult result) {
        Reservation reservation = result.reservation();
        return new ReservationCommandResponse(
                result.completed(),
                result.message(),
                ReservationResponse.from(reservation)
        );
    }
}

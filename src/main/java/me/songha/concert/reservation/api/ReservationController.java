package me.songha.concert.reservation.api;

import me.songha.concert.reservation.application.ReservationCommandResult;
import me.songha.concert.reservation.application.ReservationCommandService;
import me.songha.concert.reservation.application.ReservationQueryService;
import me.songha.concert.reservation.api.auth.AuthenticatedUser;
import me.songha.concert.reservation.domain.Reservation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class ReservationController {

    private final ReservationCommandService reservationCommandService;
    private final ReservationQueryService reservationQueryService;

    public ReservationController(
            ReservationCommandService reservationCommandService,
            ReservationQueryService reservationQueryService
    ) {
        this.reservationCommandService = reservationCommandService;
        this.reservationQueryService = reservationQueryService;
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public ResponseEntity<ReservationCommandResponse> confirm(
            @PathVariable UUID reservationId,
            AuthenticatedUser authenticatedUser
    ) {
        ReservationCommandResult result = reservationCommandService.confirm(reservationId, authenticatedUser.userId());
        ReservationCommandResponse response = toCommandResponse(result);
        if (!result.completed()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationCommandResponse cancel(
            @PathVariable UUID reservationId,
            AuthenticatedUser authenticatedUser
    ) {
        return toCommandResponse(reservationCommandService.cancel(reservationId, authenticatedUser.userId()));
    }

    @GetMapping("/reservations/{reservationId}")
    public ReservationResponse getReservation(@PathVariable UUID reservationId) {
        return ReservationResponse.from(reservationQueryService.getReservation(reservationId));
    }

    @GetMapping("/users/{userId}/reservations")
    public List<ReservationResponse> getUserReservations(@PathVariable String userId) {
        return reservationQueryService.getReservationsByUser(userId)
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

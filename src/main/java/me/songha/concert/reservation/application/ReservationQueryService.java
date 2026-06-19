package me.songha.concert.reservation.application;

import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationQueryService {

    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public Reservation getReservation(UUID reservationId, String authenticatedUserId) {
        Reservation reservation = reservationRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (!reservation.getUserId().equals(authenticatedUserId)) {
            throw new ReservationAccessDeniedException("Reservation owner mismatch.");
        }
        return reservation;
    }

    @Transactional(readOnly = true)
    public List<Reservation> getReservationsByUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank.");
        }
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}

package me.songha.concert.reservation.service;

import lombok.RequiredArgsConstructor;
import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.dto.ReservationResponse;
import me.songha.concert.reservation.exception.ReservationAccessDeniedException;
import me.songha.concert.reservation.exception.ReservationNotFoundException;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationReadService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    public ReservationResponse getReservation(UUID reservationId, String authenticatedUserId) {
        Reservation reservation = reservationRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (!reservation.getUserId().equals(authenticatedUserId)) {
            throw new ReservationAccessDeniedException("Reservation owner mismatch.");
        }
        return ReservationResponse.from(
                reservation,
                reservationSeatRepository.findByReservationIdOrderBySeatIdAsc(reservation.getReservationId())
        );
    }

    public List<ReservationResponse> getReservations(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank.");
        }
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(reservation -> ReservationResponse.from(
                        reservation,
                        reservationSeatRepository.findByReservationIdOrderBySeatIdAsc(reservation.getReservationId())
                ))
                .toList();
    }

}

package me.songha.concert.reservation.service;

import lombok.RequiredArgsConstructor;
import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.domain.ReservationSeat;
import me.songha.concert.reservation.repository.ReservationSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationSeatService {

    private final ReservationSeatRepository reservationSeatRepository;

    public List<ReservationSeat> getSeats(Reservation reservation) {
        return reservationSeatRepository.findByReservationIdOrderBySeatIdAsc(reservation.getReservationId());
    }
}

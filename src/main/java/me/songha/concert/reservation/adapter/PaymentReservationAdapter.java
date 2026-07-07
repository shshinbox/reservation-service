package me.songha.concert.reservation.adapter;

import lombok.RequiredArgsConstructor;
import me.songha.concert.payment.dto.PaymentReservationConfirmResult;
import me.songha.concert.payment.dto.PaymentReservationInfo;
import me.songha.concert.payment.service.PaymentReservationPort;
import me.songha.concert.reservation.domain.Reservation;
import me.songha.concert.reservation.dto.ReservationOperationResult;
import me.songha.concert.reservation.exception.ReservationNotFoundException;
import me.songha.concert.reservation.repository.ReservationRepository;
import me.songha.concert.reservation.service.ReservationOperationService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentReservationAdapter implements PaymentReservationPort {

    private final ReservationRepository reservationRepository;
    private final ReservationOperationService reservationOperationService;

    @Override
    public PaymentReservationInfo getReservationForPayment(UUID reservationId) {
        Reservation reservation = reservationRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return new PaymentReservationInfo(
                reservation.getReservationId(),
                reservation.getScheduleId(),
                reservation.getUserId()
        );
    }

    @Override
    public PaymentReservationConfirmResult confirmPaidReservation(UUID reservationId) {
        ReservationOperationResult result = reservationOperationService.confirm(reservationId);
        if (!result.completed()) {
            return PaymentReservationConfirmResult.rejected(result.message());
        }
        return PaymentReservationConfirmResult.success();
    }
}

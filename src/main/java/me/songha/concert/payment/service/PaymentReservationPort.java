package me.songha.concert.payment.service;

import me.songha.concert.payment.dto.PaymentReservationConfirmResult;
import me.songha.concert.payment.dto.PaymentReservationInfo;

import java.util.UUID;

public interface PaymentReservationPort {

    PaymentReservationInfo getReservationForPayment(UUID reservationId);

    PaymentReservationConfirmResult confirmPaidReservation(UUID reservationId);
}

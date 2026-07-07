package me.songha.concert.reservation.service;

import java.time.Instant;
import java.util.UUID;

public interface ReservationPaymentPort {

    void createReadyPayment(UUID reservationId, Instant now);
}

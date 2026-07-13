package me.songha.concert.reservation.service;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface ReservationPaymentPort {

    void createReadyPayment(UUID reservationId, Instant now);

    void cancelPayment(UUID reservationId, Instant now);

    int expirePayments(Collection<UUID> reservationIds, Instant now);
}

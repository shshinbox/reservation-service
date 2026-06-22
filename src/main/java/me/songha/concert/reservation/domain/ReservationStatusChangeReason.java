package me.songha.concert.reservation.domain;

public enum ReservationStatusChangeReason {
    SEAT_HOLD_CONFIRMED_EVENT,
    PAYMENT_PAID,
    USER_CANCELLED,
    HOLD_EXPIRED
}

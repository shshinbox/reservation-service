package me.songha.concert.reservation.domain;

public enum ReservationStatusChangeReason {
    SEAT_HOLD_HELD_EVENT,
    SEAT_HOLD_RELEASED_EVENT,
    PAYMENT_PAID,
    USER_CANCELLED,
    HOLD_EXPIRED
}

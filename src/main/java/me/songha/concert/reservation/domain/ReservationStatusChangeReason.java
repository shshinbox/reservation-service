package me.songha.concert.reservation.domain;

public enum ReservationStatusChangeReason {
    SEAT_HELD_EVENT,
    USER_CONFIRMED,
    USER_CANCELLED,
    HOLD_EXPIRED
}

package me.songha.concert.reservation.kafka;

public class SeatHoldEventConflictException extends RuntimeException {

    public SeatHoldEventConflictException(String message) {
        super(message);
    }
}

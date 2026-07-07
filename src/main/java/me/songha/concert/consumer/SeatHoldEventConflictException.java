package me.songha.concert.consumer;

public class SeatHoldEventConflictException extends RuntimeException {

    public SeatHoldEventConflictException(String message) {
        super(message);
    }
}

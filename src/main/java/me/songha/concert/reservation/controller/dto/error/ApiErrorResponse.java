package me.songha.concert.reservation.controller.dto.error;

import java.time.Instant;

public record ApiErrorResponse(
        String code,
        String message,
        Instant occurredAt
) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, Instant.now());
    }
}

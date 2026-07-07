package me.songha.concert.web;

import java.time.Instant;

public record ApiErrorResponse(
        String code,
        String message,
        Instant occurredAt
) {
    public static ApiErrorResponse of(String code, String message, Instant occurredAt) {
        return new ApiErrorResponse(code, message, occurredAt);
    }
}

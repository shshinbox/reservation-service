package me.songha.concert.web;

import lombok.RequiredArgsConstructor;
import me.songha.concert.auth.AuthenticationRequiredException;
import me.songha.concert.reservation.exception.ReservationAccessDeniedException;
import me.songha.concert.reservation.exception.ReservationConflictException;
import me.songha.concert.reservation.exception.ReservationNotFoundException;
import me.songha.concert.time.AppTimeProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AppTimeProvider appTimeProvider;

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ReservationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("RESERVATION_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ReservationConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ReservationConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("RESERVATION_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationRequired(AuthenticationRequiredException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error("AUTHENTICATION_REQUIRED", exception.getMessage()));
    }

    @ExceptionHandler(ReservationAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(ReservationAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("RESERVATION_ACCESS_DENIED", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("BAD_REQUEST", exception.getMessage()));
    }

    private ApiErrorResponse error(String code, String message) {
        return ApiErrorResponse.of(code, message, appTimeProvider.nowInstant());
    }
}

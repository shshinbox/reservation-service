package me.songha.concert.auth;

public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}

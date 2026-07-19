package com.philipphofmann.backend.exception;

/**
 * Base exception for authentication and authorization failures.
 */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}

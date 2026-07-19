package com.philipphofmann.backend.exception;

/**
 * Thrown when a registration uses an email that is already registered.
 */
public class EmailAlreadyExistsException extends AuthException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}

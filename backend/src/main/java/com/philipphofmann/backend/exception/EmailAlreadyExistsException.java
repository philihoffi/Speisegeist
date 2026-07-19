package com.philipphofmann.backend.exception;

public class EmailAlreadyExistsException extends AuthException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}

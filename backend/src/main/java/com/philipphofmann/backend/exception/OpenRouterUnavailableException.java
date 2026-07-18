package com.philipphofmann.backend.exception;

public class OpenRouterUnavailableException extends RuntimeException {
    public OpenRouterUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenRouterUnavailableException(String message) {
        super(message);
    }
}

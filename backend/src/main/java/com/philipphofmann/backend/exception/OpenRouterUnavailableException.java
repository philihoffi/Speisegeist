package com.philipphofmann.backend.exception;

/**
 * Thrown when the OpenRouter provider is unreachable or its API request fails.
 */
public class OpenRouterUnavailableException extends RuntimeException {
    public OpenRouterUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenRouterUnavailableException(String message) {
        super(message);
    }
}

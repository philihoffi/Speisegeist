package com.philipphofmann.backend.exception;

/**
 * Thrown when recipe generation or parsing of the AI response fails.
 */
public class RecipeGenerationException extends RuntimeException {
    public RecipeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RecipeGenerationException(String message) {
        super(message);
    }
}

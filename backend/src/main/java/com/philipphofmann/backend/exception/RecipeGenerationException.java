package com.philipphofmann.backend.exception;

public class RecipeGenerationException extends RuntimeException {
    public RecipeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RecipeGenerationException(String message) {
        super(message);
    }
}

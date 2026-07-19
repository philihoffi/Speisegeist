package com.philipphofmann.backend.exception;

import java.util.UUID;

/**
 * Thrown when a catalog ingredient does not exist.
 */
public class IngredientNotFoundException extends RuntimeException {
    public IngredientNotFoundException(UUID id) {
        super("Zutat nicht gefunden: " + id);
    }
}

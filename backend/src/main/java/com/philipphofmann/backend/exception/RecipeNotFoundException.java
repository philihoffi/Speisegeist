package com.philipphofmann.backend.exception;

import java.util.UUID;

/**
 * Thrown when a recipe does not exist or is not owned by the requesting user.
 */
public class RecipeNotFoundException extends RuntimeException {
    public RecipeNotFoundException(UUID id) {
        super("Rezept nicht gefunden: " + id);
    }
}

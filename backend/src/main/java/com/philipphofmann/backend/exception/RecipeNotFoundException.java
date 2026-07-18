package com.philipphofmann.backend.exception;

import java.util.UUID;

public class RecipeNotFoundException extends RuntimeException {
    public RecipeNotFoundException(UUID id) {
        super("Rezept nicht gefunden: " + id);
    }
}

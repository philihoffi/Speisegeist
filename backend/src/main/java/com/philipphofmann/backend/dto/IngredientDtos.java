package com.philipphofmann.backend.dto;

import com.philipphofmann.backend.entity.Ingredient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data transfer records for the global {@link Ingredient} catalog management.
 */
public final class IngredientDtos {

    private IngredientDtos() {
    }

    /** Payload for creating or updating a catalog ingredient. */
    public record IngredientRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 100) String warengruppe) {
    }

    /** Full representation of a catalog ingredient. */
    public record IngredientResponse(
            UUID id,
            String name,
            String warengruppe,
            LocalDateTime createdAt) {

        public static IngredientResponse from(Ingredient i) {
            return new IngredientResponse(
                    i.getId(), i.getName(), i.getWarengruppe(), i.getCreatedAt());
        }
    }
}

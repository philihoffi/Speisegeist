package com.philipphofmann.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted, AI-generated image for an {@link Ingredient} (one-to-one).
 * Same pattern as {@link RecipeImage}: stored separately to keep the
 * ingredient row slim. Images are lazy-generated on first access.
 */
@Entity
@Table(name = "ingredient_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class IngredientImage {

    @Id
    @Column(name = "ingredient_id")
    @EqualsAndHashCode.Include
    private UUID ingredientId;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "source_model", length = 100)
    private String sourceModel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
package com.philipphofmann.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted, AI-generated image for a {@link Recipe} (one-to-one). Deliberately
 * stored in its own table rather than a BYTEA column on {@code recipes}: the recipe
 * row (often loaded eagerly) stays slim, and the image bytes are only fetched on
 * explicit access.
 */
@Entity
@Table(name = "recipe_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RecipeImage {

    /** Primary key = foreign key to {@code recipes.id} (one-to-one). */
    @Id
    @Column(name = "recipe_id")
    @EqualsAndHashCode.Include
    private UUID recipeId;

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

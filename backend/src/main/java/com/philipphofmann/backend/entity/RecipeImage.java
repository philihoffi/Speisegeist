package com.philipphofmann.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persistiertes, KI-generiertes Bild zu einem {@link Recipe} (1:1). Bewusst eine eigene
 * Tabelle statt einer BYTEA-Spalte auf {@code recipes}: die (oft EAGER geladene) Rezept-Zeile
 * bleibt so schlank, die Bild-Bytes werden nur beim expliziten Abruf geladen.
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

    /** Primärschlüssel = Fremdschlüssel auf {@code recipes.id} (1:1). */
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

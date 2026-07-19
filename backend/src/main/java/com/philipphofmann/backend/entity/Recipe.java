package com.philipphofmann.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * A user-owned recipe: ingredients, ordered cooking steps, tags, and metadata.
 * Generation provenance is tracked via {@link SourceType}, the provider model, and
 * the application version that produced the recipe.
 */
@Entity
@Table(name = "recipes", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_name", columnList = "name"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recipe_ingredients", joinColumns = @JoinColumn(name = "recipe_id"))
    @OrderColumn(name = "display_order")
    @Builder.Default
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cooking_steps", joinColumns = @JoinColumn(name = "recipe_id"))
    @OrderColumn(name = "step_number")
    @Builder.Default
    private List<CookingStep> steps = new ArrayList<>();

    private Integer preparationTimeMinutes;

    private Integer cookTimeMinutes;

    @Builder.Default
    private Integer servings = 1;

    private Integer estimatedKcal;

    private Double rating;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recipe_tags", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private SourceType sourceType = SourceType.MANUAL;

    private String sourceModel;

    private String sourceVersion;

    private LocalDateTime generatedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum SourceType {
        GENERATED, MANUAL
    }
}

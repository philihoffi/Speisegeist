package com.philipphofmann.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Global ingredient catalog (master data). Each ingredient exists exactly once
 * (the name is globally unique, case-insensitive) and is referenced by its foreign
 * key from {@link RecipeIngredient}. The catalog grows automatically as new
 * ingredients appear in recipes.
 */
@Entity
@Table(name = "ingredients", indexes = {
        @Index(name = "idx_ingredients_warengruppe", columnList = "warengruppe")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 100)
    private String warengruppe;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

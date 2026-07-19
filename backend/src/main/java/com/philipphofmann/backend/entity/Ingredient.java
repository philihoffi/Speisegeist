package com.philipphofmann.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Globaler Zutaten-Katalog (Stammdaten). Jede Zutat existiert genau einmal
 * (Name global eindeutig, case-insensitive) und wird von {@link RecipeIngredient}
 * per Fremdschlüssel referenziert. Der Katalog wächst automatisch, sobald neue
 * Zutaten in Rezepten auftauchen.
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

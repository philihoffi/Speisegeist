package com.philipphofmann.backend.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.*;

/**
 * Verwendung einer Zutat innerhalb eines Rezepts. Verweist per Fremdschlüssel auf
 * die globale {@link Ingredient} (Name/Warengruppe) und trägt die rezeptspezifischen
 * Angaben Menge, Einheit und Notiz.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeIngredient {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    private Double quantity;
    private String unit;
    private String notes;
}

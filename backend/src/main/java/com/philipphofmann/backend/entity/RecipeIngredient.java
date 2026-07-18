package com.philipphofmann.backend.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeIngredient {
    private String name;
    private Double quantity;
    private String unit;
    private String warengruppe;
    private String notes;
}

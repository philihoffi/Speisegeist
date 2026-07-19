package com.philipphofmann.backend.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * A single cooking step embedded in a {@code Recipe}. The step order is managed by
 * the {@code @OrderColumn(step_number)} mapping on {@code Recipe.steps}, so this
 * type intentionally holds no explicit step number field.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CookingStep {
    private String instruction;
    private Integer durationMinutes;
}

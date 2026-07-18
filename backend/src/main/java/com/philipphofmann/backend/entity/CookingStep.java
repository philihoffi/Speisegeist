package com.philipphofmann.backend.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

// Step order is managed by the @OrderColumn(step_number) on Recipe.steps —
// a stepNumber field here would clash with that column mapping.
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CookingStep {
    private String instruction;
    private Integer durationMinutes;
}

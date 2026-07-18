package com.philipphofmann.backend.dto;

import com.philipphofmann.backend.entity.CookingStep;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.entity.RecipeIngredient;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RecipeDtos {

    private RecipeDtos() {
    }

    public record RecipeGenerationRequest(
            @NotEmpty List<@NotBlank String> ingredients,
            GenerationPreferences preferences) {
    }

    public record GenerationPreferences(
            String cuisine,
            String mealType,
            Integer cookTime,
            @Min(1) @Max(20) Integer servings) {
    }

    public record RecipeRequest(
            @NotBlank String name,
            String description,
            List<IngredientDto> ingredients,
            List<StepDto> steps,
            Integer preparationTimeMinutes,
            Integer cookTimeMinutes,
            @Min(1) Integer servings,
            Integer estimatedKcal,
            Set<String> tags) {
    }

    public record RatingRequest(@NotNull @DecimalMin("0.0") @DecimalMax("5.0") Double rating) {
    }

    public record IngredientDto(String name, Double quantity, String unit, String warengruppe, String notes) {

        public static IngredientDto from(RecipeIngredient i) {
            return new IngredientDto(i.getName(), i.getQuantity(), i.getUnit(), i.getWarengruppe(), i.getNotes());
        }

        public RecipeIngredient toEntity() {
            return RecipeIngredient.builder()
                    .name(name).quantity(quantity).unit(unit)
                    .warengruppe(warengruppe).notes(notes)
                    .build();
        }
    }

    public record StepDto(Integer stepNumber, String instruction, Integer durationMinutes) {

        public static StepDto from(CookingStep s, int index) {
            return new StepDto(index + 1, s.getInstruction(), s.getDurationMinutes());
        }

        public CookingStep toEntity() {
            return CookingStep.builder()
                    .instruction(instruction).durationMinutes(durationMinutes)
                    .build();
        }
    }

    public record RecipeSummaryResponse(
            UUID id,
            UUID userId,
            String name,
            String description,
            List<IngredientDto> ingredients,
            Integer preparationTimeMinutes,
            Integer cookTimeMinutes,
            Integer servings,
            Integer estimatedKcal,
            Double rating,
            Set<String> tags,
            Recipe.SourceType sourceType,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        public static RecipeSummaryResponse from(Recipe r) {
            return new RecipeSummaryResponse(
                    r.getId(), r.getUserId(), r.getName(), r.getDescription(),
                    r.getIngredients().stream().map(IngredientDto::from).toList(),
                    r.getPreparationTimeMinutes(), r.getCookTimeMinutes(),
                    r.getServings(), r.getEstimatedKcal(), r.getRating(), r.getTags(),
                    r.getSourceType(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    public record RecipeResponse(
            UUID id,
            UUID userId,
            String name,
            String description,
            List<IngredientDto> ingredients,
            List<StepDto> steps,
            Integer preparationTimeMinutes,
            Integer cookTimeMinutes,
            Integer servings,
            Integer estimatedKcal,
            Double rating,
            Set<String> tags,
            Recipe.SourceType sourceType,
            String sourceModel,
            LocalDateTime generatedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        public static RecipeResponse from(Recipe r) {
            return new RecipeResponse(
                    r.getId(), r.getUserId(), r.getName(), r.getDescription(),
                    r.getIngredients().stream().map(IngredientDto::from).toList(),
                    java.util.stream.IntStream.range(0, r.getSteps().size())
                            .mapToObj(i -> StepDto.from(r.getSteps().get(i), i)).toList(),
                    r.getPreparationTimeMinutes(), r.getCookTimeMinutes(),
                    r.getServings(), r.getEstimatedKcal(), r.getRating(), r.getTags(),
                    r.getSourceType(), r.getSourceModel(), r.getGeneratedAt(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    public record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int currentPage) {
    }
}

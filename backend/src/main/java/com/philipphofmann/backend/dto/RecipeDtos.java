package com.philipphofmann.backend.dto;

import com.philipphofmann.backend.entity.CookingStep;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.entity.RecipeIngredient;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Data transfer records for recipe requests, responses, and generation preferences.
 */
public final class RecipeDtos {

    private RecipeDtos() {
    }

    /** Request to generate a recipe from a list of ingredients and optional preferences. */
    public record RecipeGenerationRequest(
            @NotEmpty List<@NotBlank String> ingredients,
            GenerationPreferences preferences) {
    }

    /** Optional constraints applied to a generated recipe. */
    public record GenerationPreferences(
            String cuisine,
            String mealType,
            Integer cookTime,
            @Min(1) @Max(20) Integer servings) {
    }

    /** Payload for creating or updating a recipe manually. */
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

    /** Payload carrying a recipe star rating. */
    public record RatingRequest(@NotNull @DecimalMin("0.0") @DecimalMax("5.0") Double rating) {
    }

    /** Ingredient representation used in recipe DTOs. */
    public record IngredientDto(String name, Double quantity, String unit, String warengruppe, String notes) {

        public static IngredientDto from(RecipeIngredient i) {
            var ingredient = i.getIngredient();
            return new IngredientDto(
                    ingredient != null ? ingredient.getName() : null,
                    i.getQuantity(),
                    i.getUnit(),
                    ingredient != null ? ingredient.getWarengruppe() : null,
                    i.getNotes());
        }
    }

    /** A single cooking step representation used in recipe DTOs. */
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

    /** Lightweight recipe summary used in list/search results. */
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
            List<IngredientDto> ingredients = r.getIngredients() == null ? List.of() : r.getIngredients().stream().map(IngredientDto::from).toList();
            return new RecipeSummaryResponse(
                    r.getId(), r.getUserId(), r.getName(), r.getDescription(),
                    ingredients,
                    r.getPreparationTimeMinutes(), r.getCookTimeMinutes(),
                    r.getServings(), r.getEstimatedKcal(), r.getRating(), r.getTags(),
                    r.getSourceType(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    /** Full recipe representation including steps, used for detail views. */
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
            List<IngredientDto> ingredients = r.getIngredients() == null ? List.of()
                    : r.getIngredients().stream().map(IngredientDto::from).toList();
            List<StepDto> steps = r.getSteps() == null ? List.of()
                    : java.util.stream.IntStream.range(0, r.getSteps().size())
                            .mapToObj(i -> StepDto.from(r.getSteps().get(i), i)).toList();
            return new RecipeResponse(
                    r.getId(), r.getUserId(), r.getName(), r.getDescription(),
                    ingredients, steps,
                    r.getPreparationTimeMinutes(), r.getCookTimeMinutes(),
                    r.getServings(), r.getEstimatedKcal(), r.getRating(), r.getTags(),
                    r.getSourceType(), r.getSourceModel(), r.getGeneratedAt(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    /** Generic page envelope for paged responses. */
    public record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int currentPage) {
    }
}

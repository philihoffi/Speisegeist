package com.philipphofmann.backend.service;

import com.philipphofmann.backend.dto.RecipeDtos.*;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.entity.RecipeIngredient;
import com.philipphofmann.backend.exception.RecipeNotFoundException;
import com.philipphofmann.backend.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for recipes: generation, search, CRUD, and rating. Every
 * operation is scoped to the requesting user via their id.
 */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeGeneratorService recipeGeneratorService;
    private final IngredientService ingredientService;

    @Value("${app.version:1.0}")
    private String appVersion;

    /**
     * Generates a new recipe for the user from the given ingredients and preferences.
     *
     * @param userId  the owning user's id
     * @param request the generation request
     * @return the persisted, generated recipe
     */
    @Transactional
    public Recipe generateRecipe(UUID userId, RecipeGenerationRequest request) {
        Recipe recipe = recipeGeneratorService.generateRecipe(request.ingredients(), request.preferences());
        recipe.setUserId(userId);
        recipe.setSourceVersion(appVersion);
        return recipeRepository.save(recipe);
    }

    /**
     * Searches the user's recipes by text or tag and returns a page of summaries.
     *
     * @param userId the owning user's id
     * @param search optional free-text search
     * @param tag    optional tag filter
     * @param page   zero-based page index
     * @param size   page size
     * @return a page of recipe summaries
     */
    @Transactional(readOnly = true)
    public PageResponse<RecipeSummaryResponse> searchRecipes(UUID userId, String search, String tag, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Recipe> result;
        if (tag != null && !tag.isBlank()) {
            result = recipeRepository.findByUserIdAndTag(userId, tag, pageable);
        } else if (search != null && !search.isBlank()) {
            result = recipeRepository.findByUserIdAndSearch(userId, search, pageable);
        } else {
            result = recipeRepository.findByUserId(userId, pageable);
        }

        return new PageResponse<>(
                result.getContent().stream().map(RecipeSummaryResponse::from).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber());
    }

    /**
     * Returns a recipe by id, verifying ownership.
     *
     * @param userId   the owning user's id
     * @param recipeId the recipe id
     * @return the recipe
     */
    @Transactional(readOnly = true)
    public Recipe getRecipe(UUID userId, UUID recipeId) {
        return recipeRepository.findByIdAndUserId(recipeId, userId)
                .orElseThrow(() -> new RecipeNotFoundException(recipeId));
    }

    /**
     * Creates a manually entered recipe for the user.
     *
     * @param userId  the owning user's id
     * @param request the recipe payload
     * @return the persisted recipe
     */
    @Transactional
    public Recipe createRecipe(UUID userId, RecipeRequest request) {
        Recipe recipe = Recipe.builder()
                .userId(userId)
                .sourceType(Recipe.SourceType.MANUAL)
                .build();
        applyRequest(recipe, request, true);
        return recipeRepository.save(recipe);
    }

    /**
     * Updates an existing recipe owned by the user.
     *
     * @param userId   the owning user's id
     * @param recipeId the recipe id
     * @param request  the recipe payload
     * @return the updated recipe
     */
    @Transactional
    public Recipe updateRecipe(UUID userId, UUID recipeId, RecipeRequest request) {
        Recipe recipe = getRecipe(userId, recipeId);
        applyRequest(recipe, request, false);
        return recipeRepository.save(recipe);
    }

    /**
     * Deletes a recipe owned by the user.
     *
     * @param userId   the owning user's id
     * @param recipeId the recipe id
     */
    @Transactional
    public void deleteRecipe(UUID userId, UUID recipeId) {
        Recipe recipe = getRecipe(userId, recipeId);
        recipeRepository.delete(recipe);
    }

    /**
     * Stores the user's star rating for a recipe.
     *
     * @param userId   the owning user's id
     * @param recipeId the recipe id
     * @param rating   the rating value
     * @return the updated recipe
     */
    @Transactional
    public Recipe rateRecipe(UUID userId, UUID recipeId, double rating) {
        Recipe recipe = getRecipe(userId, recipeId);
        recipe.setRating(rating);
        return recipeRepository.save(recipe);
    }

    /**
     * Applies the request fields to the given recipe. When {@code full} is true the
     * call represents a create and missing fields fall back to sensible defaults.
     *
     * @param recipe  the recipe to update
     * @param request the incoming payload
     * @param full    {@code true} for create (applying defaults), {@code false} for update
     */
    private void applyRequest(Recipe recipe, RecipeRequest request, boolean full) {
        if (request.name() != null) recipe.setName(request.name());
        if (request.description() != null) recipe.setDescription(request.description());
        if (request.preparationTimeMinutes() != null) recipe.setPreparationTimeMinutes(request.preparationTimeMinutes());
        if (request.cookTimeMinutes() != null) recipe.setCookTimeMinutes(request.cookTimeMinutes());
        if (request.servings() != null) recipe.setServings(request.servings());
        else if (full) recipe.setServings(1);
        if (request.estimatedKcal() != null) recipe.setEstimatedKcal(request.estimatedKcal());
        if (request.tags() != null) recipe.setTags(new HashSet<>(request.tags()));

        if (request.ingredients() != null) {
            List<RecipeIngredient> ingredients = request.ingredients().stream()
                    .map(this::toRecipeIngredient).toList();
            recipe.setIngredients(new ArrayList<>(ingredients));
        }
        if (request.steps() != null) {
            List<com.philipphofmann.backend.entity.CookingStep> steps = request.steps().stream()
                    .map(StepDto::toEntity).toList();
            recipe.setSteps(new ArrayList<>(steps));
        }
    }

    /**
     * Maps a DTO ingredient onto a {@link RecipeIngredient} and links it to the catalog.
     *
     * @param dto the DTO ingredient
     * @return the persisted ingredient association
     */
    private RecipeIngredient toRecipeIngredient(IngredientDto dto) {
        return RecipeIngredient.builder()
                .ingredient(ingredientService.resolve(dto.name(), dto.warengruppe()))
                .quantity(dto.quantity())
                .unit(dto.unit())
                .notes(dto.notes())
                .build();
    }
}

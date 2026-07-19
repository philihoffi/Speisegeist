package com.philipphofmann.backend.controller;

import com.philipphofmann.backend.config.JwtAuthenticationFilter.AuthenticatedUser;
import com.philipphofmann.backend.dto.RecipeDtos.*;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.entity.RecipeImage;
import com.philipphofmann.backend.service.RecipeImageService;
import com.philipphofmann.backend.service.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

/**
 * REST endpoints for recipe management: generation, search, CRUD, rating, and image retrieval.
 */
@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final RecipeImageService recipeImageService;

    /**
     * Generates a new recipe from the given ingredients and preferences and stores it.
     *
     * @param user    the authenticated user
     * @param request the generation request
     * @return the created recipe
     */
    @PostMapping("/generate")
    public ResponseEntity<RecipeResponse> generate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RecipeGenerationRequest request) {
        Recipe recipe = recipeService.generateRecipe(user.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RecipeResponse.from(recipe));
    }

    /**
     * Searches the user's recipes by text or tag, returning a page of summaries.
     *
     * @param user   the authenticated user
     * @param search optional free-text search (name, ingredient, or tag)
     * @param tag    optional tag filter
     * @param page   zero-based page index
     * @param size   page size
     * @return a page of recipe summaries
     */
    @GetMapping
    public ResponseEntity<PageResponse<RecipeSummaryResponse>> search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(recipeService.searchRecipes(user.userId(), search, tag, page, size));
    }

    /**
     * Returns a single recipe by id.
     *
     * @param user the authenticated user
     * @param id   the recipe id
     * @return the full recipe
     */
    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(RecipeResponse.from(recipeService.getRecipe(user.userId(), id)));
    }

    /**
     * Returns the recipe image. On first access the image is generated via the AI
     * provider and persisted, then served from the database on subsequent calls.
     *
     * @param user the authenticated user
     * @param id   the recipe id
     * @return the image bytes with a long private cache-control header
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        RecipeImage image = recipeImageService.getOrCreateImage(user.userId(), id);
        MediaType mediaType = image.getContentType() != null
                ? MediaType.parseMediaType(image.getContentType())
                : MediaType.IMAGE_PNG;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(image.getData());
    }

    /**
     * Creates a recipe manually for the authenticated user.
     *
     * @param user    the authenticated user
     * @param request the recipe payload
     * @return the created recipe
     */
    @PostMapping
    public ResponseEntity<RecipeResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RecipeRequest request) {
        Recipe recipe = recipeService.createRecipe(user.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RecipeResponse.from(recipe));
    }

    /**
     * Updates an existing recipe owned by the authenticated user.
     *
     * @param user    the authenticated user
     * @param id      the recipe id
     * @param request the recipe payload
     * @return the updated recipe
     */
    @PutMapping("/{id}")
    public ResponseEntity<RecipeResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RecipeRequest request) {
        return ResponseEntity.ok(RecipeResponse.from(recipeService.updateRecipe(user.userId(), id, request)));
    }

    /**
     * Deletes a recipe owned by the authenticated user.
     *
     * @param user the authenticated user
     * @param id   the recipe id
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        recipeService.deleteRecipe(user.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Stores a star rating (0.0 - 5.0) for the given recipe.
     *
     * @param user    the authenticated user
     * @param id      the recipe id
     * @param request the rating payload
     * @return the updated recipe
     */
    @PostMapping("/{id}/rating")
    public ResponseEntity<RecipeResponse> rate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.ok(RecipeResponse.from(recipeService.rateRecipe(user.userId(), id, request.rating())));
    }
}

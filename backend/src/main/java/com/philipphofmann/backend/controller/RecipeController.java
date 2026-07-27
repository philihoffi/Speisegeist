package com.philipphofmann.backend.controller;

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
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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
     * @param request the generation request
     * @return the created recipe
     */
    @PostMapping("/generate")
    public ResponseEntity<RecipeResponse> generate(
            @Valid @RequestBody RecipeGenerationRequest request) {
        Recipe recipe = recipeService.generateRecipe(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RecipeResponse.from(recipe));
    }

    /**
     * Generates a new recipe with streaming output. The recipe is emitted as a stream
     * of newline-delimited JSON events (ingredients, steps, complete).
     *
     * @param request the generation request
     * @return a streaming response
     */
    @PostMapping("/generate-stream")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> generateStream(
            @Valid @RequestBody RecipeGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.valueOf("application/x-ndjson"))
                .body(outputStream -> {
                    try {
                        recipeService.generateRecipeStreaming(request, outputStream);
                    } catch (IOException e) {
                        throw new RuntimeException("Streaming failed", e);
                    }
                });
    }

    /**
     * Generates a batch of recipes from a free-text theme and streams them as
     * newline-delimited JSON events ({@code recipe}, {@code recipe-error},
     * {@code batch-progress}, {@code batch-complete}).
     *
     * @param request the batch generation request
     * @return a streaming response
     */
    @PostMapping("/generate-batch-stream")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> generateBatchStream(
            @Valid @RequestBody BatchRecipeGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.valueOf("application/x-ndjson"))
                .body(outputStream -> {
                    try {
                        recipeService.generateRecipeBatch(request, outputStream);
                    } catch (IOException e) {
                        throw new RuntimeException("Streaming failed", e);
                    }
                });
    }

    /**
     * Searches recipes by text or tag, returning a page of summaries.
     *
     * @param search optional free-text search (name, ingredient, or tag)
     * @param tag    optional tag filter
     * @param page   zero-based page index
     * @param size   page size
     * @return a page of recipe summaries
     */
    @GetMapping
    public ResponseEntity<PageResponse<RecipeSummaryResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(recipeService.searchRecipes(search, tag, page, size));
    }

    /**
     * Returns a single recipe by id.
     *
     * @param id   the recipe id
     * @return the full recipe
     */
    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(RecipeResponse.from(recipeService.getRecipe(id)));
    }

    /**
     * Returns the recipe image. On first access the image is generated via the AI
     * provider and persisted, then served from the database on subsequent calls.
     *
     * @param id   the recipe id
     * @return the image bytes with a long private cache-control header
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable UUID id) {
        RecipeImage image = recipeImageService.getOrCreateImage(id);
        MediaType mediaType = image.getContentType() != null
                ? MediaType.parseMediaType(image.getContentType())
                : MediaType.IMAGE_PNG;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(image.getData());
    }

    /**
     * Creates a recipe manually.
     *
     * @param request the recipe payload
     * @return the created recipe
     */
    @PostMapping
    public ResponseEntity<RecipeResponse> create(
            @Valid @RequestBody RecipeRequest request) {
        Recipe recipe = recipeService.createRecipe(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RecipeResponse.from(recipe));
    }

    /**
     * Updates an existing recipe.
     *
     * @param id      the recipe id
     * @param request the recipe payload
     * @return the updated recipe
     */
    @PutMapping("/{id}")
    public ResponseEntity<RecipeResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody RecipeRequest request) {
        return ResponseEntity.ok(RecipeResponse.from(recipeService.updateRecipe(id, request)));
    }

    /**
     * Deletes a recipe.
     *
     * @param id   the recipe id
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        recipeService.deleteRecipe(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Stores a star rating (0.0 - 5.0) for the given recipe.
     *
     * @param id      the recipe id
     * @param request the rating payload
     * @return the updated recipe
     */
    @PostMapping("/{id}/rating")
    public ResponseEntity<RecipeResponse> rate(
            @PathVariable UUID id,
            @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.ok(RecipeResponse.from(recipeService.rateRecipe(id, request.rating())));
    }
}

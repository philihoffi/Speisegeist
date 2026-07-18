package com.philipphofmann.backend.controller;

import com.philipphofmann.backend.config.JwtAuthenticationFilter.AuthenticatedUser;
import com.philipphofmann.backend.dto.RecipeDtos.*;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.service.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @PostMapping("/generate")
    public ResponseEntity<RecipeResponse> generate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RecipeGenerationRequest request) {
        Recipe recipe = recipeService.generateRecipe(user.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RecipeResponse.from(recipe));
    }

    @GetMapping
    public ResponseEntity<PageResponse<RecipeSummaryResponse>> search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(recipeService.searchRecipes(user.userId(), search, tag, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(RecipeResponse.from(recipeService.getRecipe(user.userId(), id)));
    }

    @PostMapping
    public ResponseEntity<RecipeResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RecipeRequest request) {
        Recipe recipe = recipeService.createRecipe(user.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RecipeResponse.from(recipe));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecipeResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RecipeRequest request) {
        return ResponseEntity.ok(RecipeResponse.from(recipeService.updateRecipe(user.userId(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        recipeService.deleteRecipe(user.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<RecipeResponse> rate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.ok(RecipeResponse.from(recipeService.rateRecipe(user.userId(), id, request.rating())));
    }
}

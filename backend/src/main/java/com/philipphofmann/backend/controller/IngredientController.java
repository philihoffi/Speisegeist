package com.philipphofmann.backend.controller;

import com.philipphofmann.backend.dto.IngredientDtos.IngredientRequest;
import com.philipphofmann.backend.dto.IngredientDtos.IngredientResponse;
import com.philipphofmann.backend.dto.RecipeDtos.PageResponse;
import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.entity.IngredientImage;
import com.philipphofmann.backend.service.IngredientImageService;
import com.philipphofmann.backend.service.IngredientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for managing the global ingredient catalog (list, create, update, delete).
 */
@RestController
@RequestMapping("/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientService ingredientService;
    private final IngredientImageService ingredientImageService;

    /**
     * Lists catalog ingredients, with optional name search and warengruppe filter.
     *
     * @param search      optional case-insensitive name substring
     * @param warengruppe optional exact warengruppe filter
     * @param page        zero-based page index
     * @param size        page size
     * @return a page of ingredient responses ordered by name
     */
    @GetMapping
    public ResponseEntity<PageResponse<IngredientResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String warengruppe,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<Ingredient> result = ingredientService.listIngredients(
                search, warengruppe,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
        PageResponse<IngredientResponse> body = new PageResponse<>(
                result.getContent().stream().map(IngredientResponse::from).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber());
        return ResponseEntity.ok(body);
    }

    /**
     * Lists all distinct warengruppen present in the catalog.
     *
     * @return the distinct warengruppen, alphabetically ordered
     */
    @GetMapping("/warengruppen")
    public ResponseEntity<List<String>> listWarengruppen() {
        return ResponseEntity.ok(ingredientService.listWarengruppen());
    }

    /**
     * Returns a single catalog ingredient by id.
     *
     * @param id the ingredient id
     * @return the ingredient
     */
    @GetMapping("/{id}")
    public ResponseEntity<IngredientResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(IngredientResponse.from(ingredientService.getIngredient(id)));
    }

    /**
     * Creates a new catalog ingredient.
     *
     * @param request the ingredient payload
     * @return the created ingredient
     */
    @PostMapping
    public ResponseEntity<IngredientResponse> create(@Valid @RequestBody IngredientRequest request) {
        Ingredient created = ingredientService.createIngredient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(IngredientResponse.from(created));
    }

    /**
     * Updates an existing catalog ingredient.
     *
     * @param id      the ingredient id
     * @param request the ingredient payload
     * @return the updated ingredient
     */
    @PutMapping("/{id}")
    public ResponseEntity<IngredientResponse> update(
            @PathVariable UUID id, @Valid @RequestBody IngredientRequest request) {
        return ResponseEntity.ok(IngredientResponse.from(ingredientService.updateIngredient(id, request)));
    }

    /**
     * Deletes a catalog ingredient.
     *
     * @param id the ingredient id
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ingredientService.deleteIngredient(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the AI-generated image for a catalog ingredient. On first access
     * the image is generated via OpenRouter and persisted; subsequent accesses
     * serve the cached version directly.
     *
     * @param id the ingredient id
     * @return the image bytes with a long private cache-control header
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable UUID id) {
        IngredientImage image = ingredientImageService.getOrCreateImage(id);
        MediaType mediaType = image.getContentType() != null
                ? MediaType.parseMediaType(image.getContentType())
                : MediaType.IMAGE_PNG;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(image.getData());
    }
}

package com.philipphofmann.backend.service;

import com.philipphofmann.backend.dto.IngredientDtos.IngredientRequest;
import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.exception.IngredientNotFoundException;
import com.philipphofmann.backend.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Maintains the global {@link Ingredient} catalog. Its main responsibility is
 * {@link #resolve(String, String)}: find an ingredient by name or create it, so
 * the catalog gradually collects every ingredient that appears in recipes.
 * Additionally it exposes CRUD operations for the management UI.
 */
@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredientRepository;

    /**
     * Returns the catalog ingredient for the name (case-insensitive) or creates it.
     * A missing warengruppe is backfilled, but an existing one is left untouched.
     * Runs within the caller's transaction.
     *
     * @param rawName        the ingredient name (will be trimmed)
     * @param warengruppe    optional warengruppe used for shopping-list grouping
     * @return the persisted (managed) ingredient
     */
    @Transactional
    public Ingredient resolve(String rawName, String warengruppe) {
        String name = rawName == null ? "" : rawName.trim();
        String normalizedWarengruppe = (warengruppe == null || warengruppe.isBlank()) ? null : warengruppe.trim();

        return ingredientRepository.findByNameIgnoreCase(name)
                .map(existing -> {
                    if (existing.getWarengruppe() == null && normalizedWarengruppe != null) {
                        existing.setWarengruppe(normalizedWarengruppe);
                    }
                    return existing;
                })
                .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                        .name(name)
                        .warengruppe(normalizedWarengruppe)
                        .build()));
    }

    /**
     * Returns a page of catalog ingredients, optionally filtered by a case-insensitive
     * substring search on the name and an exact warengruppe filter.
     *
     * @param search       optional name search term
     * @param warengruppe  optional exact warengruppe filter
     * @param pageable     pagination/sort specification
     * @return a page of ingredients ordered by name
     */
    @Transactional(readOnly = true)
    public Page<Ingredient> listIngredients(String search, String warengruppe, Pageable pageable) {
        if (warengruppe != null && !warengruppe.isBlank()) {
            return ingredientRepository.findByWarengruppeIgnoreCaseOrderByNameAsc(
                    warengruppe, pageable);
        }
        if (search != null && !search.isBlank()) {
            return ingredientRepository.findByNameContainingIgnoreCaseOrderByNameAsc(search, pageable);
        }
        return ingredientRepository.findAllByOrderByNameAsc(pageable);
    }

    /**
     * Returns all distinct warengruppen present in the catalog, ordered alphabetically.
     *
     * @return the distinct warengruppen
     */
    @Transactional(readOnly = true)
    public List<String> listWarengruppen() {
        return ingredientRepository.findDistinctWarengruppen();
    }

    /**
     * Returns a single catalog ingredient by id.
     *
     * @param id the ingredient id
     * @return the ingredient
     * @throws IngredientNotFoundException if no ingredient with that id exists
     */
    @Transactional(readOnly = true)
    public Ingredient getIngredient(UUID id) {
        return ingredientRepository.findById(id)
                .orElseThrow(() -> new IngredientNotFoundException(id));
    }

    /**
     * Creates a new catalog ingredient.
     *
     * @param request the ingredient payload
     * @return the created ingredient
     * @throws DataIntegrityViolationException if a case-insensitive name duplicate exists
     */
    @Transactional
    public Ingredient createIngredient(IngredientRequest request) {
        Ingredient ingredient = Ingredient.builder()
                .name(request.name().trim())
                .warengruppe(normalizeWarengruppe(request.warengruppe()))
                .build();
        return ingredientRepository.save(ingredient);
    }

    /**
     * Updates an existing catalog ingredient.
     *
     * @param id      the ingredient id
     * @param request the ingredient payload
     * @return the updated ingredient
     * @throws IngredientNotFoundException     if no ingredient with that id exists
     * @throws DataIntegrityViolationException if a case-insensitive name duplicate exists
     */
    @Transactional
    public Ingredient updateIngredient(UUID id, IngredientRequest request) {
        Ingredient existing = getIngredient(id);
        existing.setName(request.name().trim());
        existing.setWarengruppe(normalizeWarengruppe(request.warengruppe()));
        return ingredientRepository.save(existing);
    }

    /**
     * Deletes a catalog ingredient by id.
     *
     * @param id the ingredient id
     * @throws IngredientNotFoundException if no ingredient with that id exists
     */
    @Transactional
    public void deleteIngredient(UUID id) {
        if (!ingredientRepository.existsById(id)) {
            throw new IngredientNotFoundException(id);
        }
        ingredientRepository.deleteById(id);
    }

    private static String normalizeWarengruppe(String warengruppe) {
        return (warengruppe == null || warengruppe.isBlank()) ? null : warengruppe.trim();
    }
}

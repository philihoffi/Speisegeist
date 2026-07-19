package com.philipphofmann.backend.service;

import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the global {@link Ingredient} catalog. Its main responsibility is
 * {@link #resolve(String, String)}: find an ingredient by name or create it, so
 * the catalog gradually collects every ingredient that appears in recipes.
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
}

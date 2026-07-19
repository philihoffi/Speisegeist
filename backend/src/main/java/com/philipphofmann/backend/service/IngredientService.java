package com.philipphofmann.backend.service;

import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pflegt den globalen {@link Ingredient}-Katalog. Zentrale Aufgabe ist
 * {@link #resolve(String, String)}: eine Zutat anhand ihres Namens finden oder
 * neu anlegen, sodass der Katalog über die Zeit alle vorkommenden Zutaten sammelt.
 */
@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredientRepository;

    /**
     * Liefert die Katalog-Zutat zum Namen (case-insensitive) oder legt sie neu an.
     * Eine bislang fehlende Warengruppe wird nachgetragen, eine bestehende bleibt
     * unangetastet. Läuft in der Transaktion des Aufrufers mit.
     *
     * @param rawName     Zutatenname (wird getrimmt)
     * @param warengruppe Warengruppe für die Einkaufslisten-Gruppierung, optional
     * @return die persistente (managed) Zutat
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

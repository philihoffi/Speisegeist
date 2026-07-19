package com.philipphofmann.backend.service;

import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.entity.IngredientImage;
import com.philipphofmann.backend.repository.IngredientImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Generates and caches AI images for catalog ingredients. The first time an
 * ingredient's image is requested, a prompt is built from the ingredient's
 * name and warengruppe and sent to {@link OpenRouterService}. Subsequent
 * requests serve the persisted image directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientImageService {

    private final IngredientService ingredientService;
    private final IngredientImageRepository ingredientImageRepository;
    private final OpenRouterService openRouterService;

    @Value("${openrouter.image-size:1024x1024}")
    private String imageSize;

    /**
     * Returns the (possibly newly generated) image for the given ingredient.
     * Not transactional: the OpenRouter call may be slow and should not hold
     * a DB connection.
     */
    public IngredientImage getOrCreateImage(UUID ingredientId) {
        return ingredientImageRepository.findById(ingredientId)
                .orElseGet(() -> {
                    Ingredient ingredient = ingredientService.getIngredient(ingredientId);
                    return generateAndStore(ingredient);
                });
    }

    private IngredientImage generateAndStore(Ingredient ingredient) {
        String prompt = buildPrompt(ingredient);
        log.debug("Generiere Bild für Zutat {} ({})", ingredient.getId(), ingredient.getName());

        OpenRouterService.GeneratedImage generated = openRouterService.generateImage(prompt, imageSize, 1);

        IngredientImage image = IngredientImage.builder()
                .ingredientId(ingredient.getId())
                .data(generated.data())
                .contentType(generated.mediaType())
                .prompt(prompt)
                .sourceModel(openRouterService.getImageModel())
                .build();

        return ingredientImageRepository.save(image);
    }

    private String buildPrompt(Ingredient ingredient) {
        StringBuilder sb = new StringBuilder(
                "Fotorealistisches, appetitliches Food-Foto der einzelnen Zutat \"")
                .append(ingredient.getName()).append("\"");
        if (ingredient.getWarengruppe() != null && !ingredient.getWarengruppe().isBlank()) {
            sb.append(" (Kategorie: ").append(ingredient.getWarengruppe()).append(")");
        }

        sb.append("\n\nDie Zutat soll pur, unverarbeitet und einzeln auf einem neutralen ")
                .append("Holzbrett oder Teller fotografiert sein. Natürliches Licht, ")
                .append("Draufsicht oder leichter Winkel, hohe Detailschärfe. ")
                .append("KEINE weiteren Zutaten, Gewürze oder Garnierungen hinzufügen. ")
                .append("Nur genau diese eine Zutat zeigen.");
        return sb.toString();
    }
}
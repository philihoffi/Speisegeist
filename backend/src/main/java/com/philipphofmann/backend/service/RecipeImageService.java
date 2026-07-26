package com.philipphofmann.backend.service;

import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.entity.RecipeImage;
import com.philipphofmann.backend.entity.RecipeIngredient;
import com.philipphofmann.backend.repository.RecipeImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Liefert das Bild zu einem Rezept aus der Datenbank. Beim ersten Abruf wird das Bild
 * über {@link OpenRouterService} generiert, heruntergeladen und persistiert; danach
 * kommt es ohne weiteren Provider-Aufruf direkt aus der DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeImageService {

    private final RecipeService recipeService;
    private final RecipeImageRepository recipeImageRepository;
    private final OpenRouterService openRouterService;

    @Value("${openrouter.image-size:1024x1024}")
    private String imageSize;

    /**
     * Liefert das (ggf. neu generierte) Bild eines Rezepts. Prüft implizit über
     * {@link RecipeService#getRecipe}, dass das Rezept existiert.
     *
     * <p>Bewusst nicht {@code @Transactional}: der (langsame) OpenRouter-Aufruf soll keine
     * DB-Verbindung offen halten. DB-Zugriffe (findById/save) laufen jeweils in ihrer eigenen
     * Repository-Transaktion.
     */
    public RecipeImage getOrCreateImage(UUID recipeId) {
        Recipe recipe = recipeService.getRecipe(recipeId);

        return recipeImageRepository.findById(recipeId)
                .orElseGet(() -> generateAndStore(recipe));
    }

    private RecipeImage generateAndStore(Recipe recipe) {
        String prompt = buildPrompt(recipe);
        log.debug("Generiere Bild für Rezept {} ({})", recipe.getId(), recipe.getName());

        OpenRouterService.GeneratedImage generated = openRouterService.generateImage(prompt, imageSize, 1);

        RecipeImage image = RecipeImage.builder()
                .recipeId(recipe.getId())
                .data(generated.data())
                .contentType(generated.mediaType())
                .prompt(prompt)
                .sourceModel(openRouterService.getImageModel())
                .build();

        return recipeImageRepository.save(image);
    }

    private String buildPrompt(Recipe recipe) {
        StringBuilder sb = new StringBuilder(
                "Professional food photography of \"")
                .append(recipe.getName()).append("\"");

        if (recipe.getDescription() != null && !recipe.getDescription().isBlank()) {
            sb.append(". ").append(recipe.getDescription());
        }

        // Add a few of the most visually prominent ingredients as scene cues (max 4)
        if (recipe.getIngredients() != null && !recipe.getIngredients().isEmpty()) {
            String cues = recipe.getIngredients().stream()
                    .limit(4)
                    .filter(ri -> ri.getIngredient() != null && ri.getIngredient().getName() != null)
                    .map(ri -> ri.getIngredient().getName())
                    .collect(java.util.stream.Collectors.joining(", "));
            if (!cues.isBlank()) {
                sb.append(" Featuring ").append(cues).append(".");
            }
        }

        sb.append(" Elegant plating on a matte ceramic plate or rustic wooden board. ")
                .append("Natural side lighting from the left, shallow depth of field, ")
                .append("45-degree angle, warm and inviting atmosphere. ")
                .append("Garnished tastefully. High-end food magazine style. ")
                .append("No text, no watermarks, photorealistic.");
        return sb.toString();
    }
}

package com.philipphofmann.backend.service;

import com.philipphofmann.backend.entity.CookingStep;
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
     * {@link RecipeService#getRecipe} den Eigentümer und dass das Rezept existiert.
     *
     * <p>Bewusst nicht {@code @Transactional}: der (langsame) OpenRouter-Aufruf soll keine
     * DB-Verbindung offen halten. DB-Zugriffe (findById/save) laufen jeweils in ihrer eigenen
     * Repository-Transaktion.
     */
    public RecipeImage getOrCreateImage(UUID userId, UUID recipeId) {
        Recipe recipe = recipeService.getRecipe(userId, recipeId);

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
        StringBuilder sb = new StringBuilder("Appetitliche, fotorealistische Food-Fotografie des Gerichts \"")
                .append(recipe.getName())
                .append("\"");
        if (recipe.getDescription() != null && !recipe.getDescription().isBlank()) {
            sb.append(": ").append(recipe.getDescription());
        }

        if (recipe.getIngredients() != null && !recipe.getIngredients().isEmpty()) {
            sb.append("\n\nZutaten (exakt diese, keine weiteren erfinden): ");
            for (RecipeIngredient ri : recipe.getIngredients()) {
                sb.append("\n- ");
                if (ri.getQuantity() != null) {
                    sb.append(ri.getQuantity());
                }
                if (ri.getUnit() != null && !ri.getUnit().isBlank()) {
                    sb.append(" ").append(ri.getUnit());
                }
                if (ri.getIngredient() != null && ri.getIngredient().getName() != null) {
                    sb.append(" ").append(ri.getIngredient().getName());
                }
                if (ri.getNotes() != null && !ri.getNotes().isBlank()) {
                    sb.append(" (").append(ri.getNotes()).append(")");
                }
            }
        }

        if (recipe.getSteps() != null && !recipe.getSteps().isEmpty()) {
            sb.append("\n\nZubereitung: ");
            for (int i = 0; i < recipe.getSteps().size(); i++) {
                CookingStep step = recipe.getSteps().get(i);
                if (step.getInstruction() != null && !step.getInstruction().isBlank()) {
                    sb.append("\n").append(i + 1).append(". ").append(step.getInstruction());
                }
            }
        }

        sb.append("\n\nWichtig: Stelle das Gericht ausschließlich mit exakt den genannten Zutaten dar. ")
                .append("Erfinde oder ergänze KEINE weiteren Zutaten, Garnierungen oder Beilagen, ")
                .append("die nicht in der Zutatenliste stehen. Nur die angegebenen Zutaten dürfen sichtbar sein.")
                .append("\nNatürliches Licht, angerichtet auf einem Teller, Draufsicht, hohe Detailschärfe.");
        return sb.toString();
    }
}

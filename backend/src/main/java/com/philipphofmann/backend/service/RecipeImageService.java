package com.philipphofmann.backend.service;

import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.entity.RecipeImage;
import com.philipphofmann.backend.repository.RecipeImageRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Liefert das Bild zu einem Rezept aus der Datenbank. Beim ersten Abruf wird das Bild
 * über {@link OpenRouterService} generiert, heruntergeladen und persistiert; danach
 * kommt es ohne weiteren Provider-Aufruf direkt aus der DB.
 */
@Service
public class RecipeImageService extends AbstractImageGenerationService<Recipe, RecipeImage> {

    private final RecipeService recipeService;

    public RecipeImageService(RecipeService recipeService, RecipeImageRepository recipeImageRepository,
                               OpenRouterService openRouterService) {
        super(recipeImageRepository, openRouterService);
        this.recipeService = recipeService;
    }

    @Override
    protected Recipe fetchEntity(UUID id) {
        return recipeService.getRecipe(id);
    }

    @Override
    protected String describeEntity(Recipe recipe) {
        return "Rezept " + recipe.getId() + " (" + recipe.getName() + ")";
    }

    @Override
    protected RecipeImage newImage(Recipe recipe, OpenRouterService.GeneratedImage generated, String prompt, String sourceModel) {
        return RecipeImage.builder()
                .recipeId(recipe.getId())
                .data(generated.data())
                .contentType(generated.mediaType())
                .prompt(prompt)
                .sourceModel(sourceModel)
                .build();
    }

    @Override
    protected String buildPrompt(Recipe recipe) {
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

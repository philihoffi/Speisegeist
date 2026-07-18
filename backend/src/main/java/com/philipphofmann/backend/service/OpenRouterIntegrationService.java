package com.philipphofmann.backend.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.philipphofmann.backend.dto.RecipeDtos.GenerationPreferences;
import com.philipphofmann.backend.entity.CookingStep;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.entity.RecipeIngredient;
import com.philipphofmann.backend.exception.OpenRouterUnavailableException;
import com.philipphofmann.backend.exception.RecipeGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRouterIntegrationService {

    private final RestClient openRouterRestClient;
    private final ObjectMapper objectMapper;

    @Value("${openrouter.model:mistralai/mistral-small-3.1-24b-instruct}")
    private String model;

    @Value("${openrouter.api.retry-max-attempts:3}")
    private int maxAttempts;

    private static final String SYSTEM_PROMPT = """
            Du bist ein Koch-Assistent für vegane Küche. Erzeuge aus den gegebenen Zutaten \
            ein veganes Rezept. Antworte AUSSCHLIESSLICH mit validem JSON in diesem Format, ohne Markdown:
            {
              "name": "Rezeptname",
              "description": "Kurzbeschreibung",
              "ingredients": [{"name": "...", "quantity": 200, "unit": "g", "warengruppe": "Gemüse", "notes": null}],
              "steps": [{"stepNumber": 1, "instruction": "...", "durationMinutes": 5}],
              "preparationTimeMinutes": 15,
              "cookTimeMinutes": 30,
              "servings": 2,
              "estimatedKcal": 550,
              "tags": ["vegan", "schnell"]
            }
            Alle Texte auf Deutsch. Nährwerte sind Schätzungen.""";

    public Recipe generateRecipe(List<String> ingredients, GenerationPreferences preferences) {
        String userPrompt = buildUserPrompt(ingredients, preferences);

        RestClientResponseException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String content = callOpenRouter(userPrompt);
                return parseRecipeJson(content);
            } catch (RestClientResponseException e) {
                lastError = e;
                int status = e.getStatusCode().value();
                if (attempt < maxAttempts && (status == 429 || status >= 500)) {
                    sleepBackoff(attempt);
                    continue;
                }
                break;
            } catch (ResourceAccessException e) {
                if (attempt < maxAttempts) {
                    sleepBackoff(attempt);
                    continue;
                }
                throw new OpenRouterUnavailableException("OpenRouter nicht erreichbar", e);
            }
        }
        if (lastError != null) {
            throw new OpenRouterUnavailableException(
                    "OpenRouter-Anfrage fehlgeschlagen: " + lastError.getStatusCode(), lastError);
        }
        throw new OpenRouterUnavailableException("OpenRouter nicht erreichbar");
    }

    private String callOpenRouter(String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)));

        JsonNode response = openRouterRestClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("choices").isEmpty()) {
            throw new RecipeGenerationException("Leere Antwort von OpenRouter");
        }
        return response.path("choices").get(0).path("message").path("content").asText();
    }

    private Recipe parseRecipeJson(String content) {
        try {
            // Strip markdown code fences the model may add despite instructions
            String json = content.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(json)?\\s*", "").replaceAll("```\\s*$", "");
            }

            JsonNode node = objectMapper.readTree(json);
            if (!node.hasNonNull("name") || node.path("ingredients").isEmpty() || node.path("steps").isEmpty()) {
                throw new RecipeGenerationException("Unvollständiges Rezept-JSON von der KI");
            }

            List<RecipeIngredient> ingredients = new ArrayList<>();
            for (JsonNode i : node.path("ingredients")) {
                ingredients.add(RecipeIngredient.builder()
                        .name(i.path("name").asText())
                        .quantity(i.hasNonNull("quantity") ? i.path("quantity").asDouble() : null)
                        .unit(i.hasNonNull("unit") ? i.path("unit").asText() : null)
                        .warengruppe(i.hasNonNull("warengruppe") ? i.path("warengruppe").asText() : null)
                        .notes(i.hasNonNull("notes") ? i.path("notes").asText() : null)
                        .build());
            }

            List<JsonNode> stepNodes = new ArrayList<>();
            node.path("steps").forEach(stepNodes::add);
            stepNodes.sort(java.util.Comparator.comparingInt(s -> s.path("stepNumber").asInt()));

            List<CookingStep> steps = new ArrayList<>();
            for (JsonNode s : stepNodes) {
                steps.add(CookingStep.builder()
                        .instruction(s.path("instruction").asText())
                        .durationMinutes(s.hasNonNull("durationMinutes") ? s.path("durationMinutes").asInt() : null)
                        .build());
            }

            Set<String> tags = new HashSet<>();
            node.path("tags").forEach(t -> tags.add(t.asText()));

            return Recipe.builder()
                    .name(node.path("name").asText())
                    .description(node.hasNonNull("description") ? node.path("description").asText() : null)
                    .ingredients(ingredients)
                    .steps(steps)
                    .preparationTimeMinutes(node.hasNonNull("preparationTimeMinutes")
                            ? node.path("preparationTimeMinutes").asInt() : null)
                    .cookTimeMinutes(node.hasNonNull("cookTimeMinutes") ? node.path("cookTimeMinutes").asInt() : null)
                    .servings(node.hasNonNull("servings") ? node.path("servings").asInt() : 2)
                    .estimatedKcal(node.hasNonNull("estimatedKcal") ? node.path("estimatedKcal").asInt() : null)
                    .tags(tags)
                    .sourceType(Recipe.SourceType.GENERATED)
                    .sourceModel(model)
                    .generatedAt(LocalDateTime.now())
                    .build();
        } catch (RecipeGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse recipe JSON: {}", content, e);
            throw new RecipeGenerationException("KI-Antwort konnte nicht geparst werden", e);
        }
    }

    private String buildUserPrompt(List<String> ingredients, GenerationPreferences prefs) {
        StringBuilder sb = new StringBuilder("Zutaten: ").append(String.join(", ", ingredients));
        if (prefs != null) {
            if (prefs.cuisine() != null) sb.append("\nKüche: ").append(prefs.cuisine());
            if (prefs.mealType() != null) sb.append("\nMahlzeit: ").append(prefs.mealType());
            if (prefs.cookTime() != null) sb.append("\nMax. Kochzeit: ").append(prefs.cookTime()).append(" Minuten");
            if (prefs.servings() != null) sb.append("\nPortionen: ").append(prefs.servings());
        }
        return sb.toString();
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(1000L * (1L << (attempt - 1)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

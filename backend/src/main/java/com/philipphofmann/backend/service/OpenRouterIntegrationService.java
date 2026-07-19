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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRouterIntegrationService {

    private final RestClient openRouterRestClient;
    private final ObjectMapper objectMapper;

    @Value("${openrouter.model:openai/gpt-4o-mini}")
    private String model;

    @Value("${openrouter.api.retry-max-attempts:3}")
    private int maxAttempts;

    @Value("${openrouter.max-tokens:2000}")
    private int maxTokens;

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

    /**
     * Erzwingt via OpenRouter Structured Outputs, dass die KI ausschließlich JSON
     * exakt in der vom Parser erwarteten Struktur liefert (strict = alle Felder
     * required, keine zusätzlichen Properties).
     */
    private static final Map<String, Object> RESPONSE_FORMAT = buildResponseFormat();

    private static Map<String, Object> buildResponseFormat() {
        Map<String, Object> ingredientProps = orderedMap(
                "name", type("string"),
                "quantity", nullable("number"),
                "unit", nullable("string"),
                "warengruppe", nullable("string"),
                "notes", nullable("string"));

        Map<String, Object> stepProps = orderedMap(
                "stepNumber", type("integer"),
                "instruction", type("string"),
                "durationMinutes", nullable("integer"));

        Map<String, Object> recipeProps = orderedMap(
                "name", type("string"),
                "description", nullable("string"),
                "ingredients", array(object(ingredientProps)),
                "steps", array(object(stepProps)),
                "preparationTimeMinutes", nullable("integer"),
                "cookTimeMinutes", nullable("integer"),
                "servings", type("integer"),
                "estimatedKcal", nullable("integer"),
                "tags", array(type("string")));

        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "recipe",
                        "strict", true,
                        "schema", object(recipeProps)));
    }

    private static Map<String, Object> type(String jsonType) {
        return Map.of("type", jsonType);
    }

    private static Map<String, Object> nullable(String jsonType) {
        return Map.of("type", List.of(jsonType, "null"));
    }

    private static Map<String, Object> array(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    /** Objekt-Schema im strict-Modus: additionalProperties=false, alle Keys required. */
    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.copyOf(properties.keySet()),
                "properties", properties);
    }

    /** Wie {@link Map#of} aber mit stabiler Einfüge-Reihenfolge (für {@code required}). */
    private static Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

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
                        Map.of("role", "user", "content", userPrompt)),
                "response_format", RESPONSE_FORMAT,
                "max_tokens", maxTokens,
                // Nur Provider zulassen, die response_format/json_schema wirklich unterstützen,
                // sonst routet OpenRouter ggf. an eine Instanz, die den Parameter ignoriert/ablehnt
                // (Folge: finish_reason=error). Siehe openrouter.ai/docs/features/structured-outputs
                "provider", Map.of("require_parameters", true));

        JsonNode response = openRouterRestClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new RecipeGenerationException("Leere Antwort von OpenRouter");
        }

        // OpenRouter meldet Provider-Fehler entweder top-level oder pro Choice als error-Objekt,
        // oft zusammen mit HTTP 200 und finish_reason=error.
        JsonNode choice = response.path("choices").get(0);
        JsonNode topError = response.path("error");
        JsonNode choiceError = choice != null ? choice.path("error") : null;
        String finishReason = choice != null ? choice.path("finish_reason").asText("") : "";

        if (topError.isObject() || (choiceError != null && choiceError.isObject()) || "error".equals(finishReason)) {
            log.warn("OpenRouter-Fehlerantwort: {}", response);
            JsonNode err = topError.isObject() ? topError : choiceError;
            String message = err != null ? err.path("message").asText("unbekannter Fehler") : "unbekannter Fehler";
            throw new RecipeGenerationException(
                    "OpenRouter/Provider-Fehler bei der Generierung (finish_reason=" + finishReason + "): " + message);
        }

        if (choice == null) {
            throw new RecipeGenerationException("Leere Antwort von OpenRouter");
        }

        String content = choice.path("message").path("content").asText();

        log.debug("OpenRouter finish_reason={}, completion_tokens={}, content_length={}",
                finishReason,
                response.path("usage").path("completion_tokens").asInt(-1),
                content.length());

        if ("length".equals(finishReason)) {
            throw new RecipeGenerationException(
                    "KI-Antwort wurde durch das Token-Limit abgeschnitten (max_tokens=" + maxTokens
                            + "). Erhöhe openrouter.max-tokens.");
        }
        return content;
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

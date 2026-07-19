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
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestriert die Rezept-Generierung: baut die Prompts, ruft
 * {@link OpenRouterService} mit Retry/Backoff auf und mappt das KI-JSON auf
 * eine {@link Recipe}-Entität.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeGeneratorService {

    private final OpenRouterService openRouterService;
    private final ObjectMapper objectMapper;

    @Value("${openrouter.api.retry-max-attempts:3}")
    private int maxAttempts;

    // Das Antwortformat wird bereits per json_schema (response_format) erzwungen.
    // Dieser Prompt steuert daher nur Inhalt und Qualität, nicht die Struktur.
    private static final String SYSTEM_PROMPT = """
            Du bist ein erfahrener Koch-Assistent für vegane Küche. Erstelle aus den \
            angegebenen Zutaten ein vollständiges, alltagstaugliches veganes Rezept.

            Grundregeln:
            - STRIKT VEGAN: keine tierischen Produkte. Nennt der Nutzer tierische Zutaten \
            (z. B. Hähnchen, Butter, Ei, Käse, Honig), ersetze sie durch eine passende \
            vegane Alternative und mache das im notes-Feld transparent (z. B. \
            "Räuchertofu als Ersatz für Hähnchen").
            - Die angegebenen Zutaten bilden den Kern des Rezepts. Du darfst sinnvoll \
            ergänzende Zutaten hinzufügen, um ein vollwertiges Gericht zu erhalten \
            (z. B. Gemüse, Gewürze, Bindemittel).
            - Beachte die Nutzer-Vorgaben (Küche/Stil, Mahlzeit, maximale Kochzeit, Portionen), \
            sofern angegeben. Die maximale Kochzeit darf nicht überschritten werden.

            Qualität und Präzision:
            - Gib für jede Zutat eine realistische Menge mit metrischer Einheit an \
            (g, ml, EL, TL, Stück, Prise) und skaliere alle Mengen auf die gewünschte Portionenzahl.
            - Schreibe klare, konkrete Handlungsschritte in sinnvoller Reihenfolge, \
            fortlaufend nummeriert ab stepNumber 1. Nenne, wo relevant, Temperatur, \
            Gardauer, Zielkonsistenz und Topf-/Pfannengröße.
            - Schätze preparationTimeMinutes (Vorbereitung) und cookTimeMinutes (Garen) \
            getrennt und realistisch.
            - estimatedKcal ist die geschätzte Kalorienzahl pro Portion.

            Deutsche Konventionen:
            - Alle Texte auf Deutsch, deutsche Zutaten- und Gerichtnamen.
            - Wähle die warengruppe jeder Zutat aus dieser Liste (für die Einkaufslisten-Gruppierung): \
            Gemüse, Obst, Getreide & Backwaren, Hülsenfrüchte, Tofu & Sojaprodukte, \
            Nüsse & Samen, Pflanzliche Milch & Alternativen, Gewürze & Kräuter, \
            Öle & Fette, Süßungsmittel, Konserven & Eingemachtes, Sonstiges.
            - tags: kurze, kleingeschriebene deutsche Schlagworte (z. B. "vegan", "schnell", "proteinreich").
            - description: 1-2 appetitliche Sätze.

            Optionale Felder ohne sinnvollen Wert auf null setzen (nicht raten). \
            Nährwerte sind Schätzungen.""";

    /**
     * Erzwingt via OpenRouter Structured Outputs, dass die KI ausschließlich JSON
     * im Rezept-Schema liefert (strict = alle Felder required, keine zusätzlichen
     * Properties).
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
                String content = openRouterService.complete(
                        SYSTEM_PROMPT,
                        List.of(OpenRouterService.Message.user(userPrompt)),
                        RESPONSE_FORMAT);
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

            String model = openRouterService.getModel();

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
        StringBuilder sb = new StringBuilder("Verfügbare Zutaten: ").append(String.join(", ", ingredients));
        if (prefs != null) {
            if (prefs.cuisine() != null) sb.append("\nKüche/Stil: ").append(prefs.cuisine());
            if (prefs.mealType() != null) sb.append("\nMahlzeit: ").append(prefs.mealType());
            if (prefs.cookTime() != null) {
                sb.append("\nMaximale Kochzeit: ").append(prefs.cookTime()).append(" Minuten (nicht überschreiten)");
            }
            if (prefs.servings() != null) {
                sb.append("\nPortionen: ").append(prefs.servings()).append(" (Mengen entsprechend skalieren)");
            }
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

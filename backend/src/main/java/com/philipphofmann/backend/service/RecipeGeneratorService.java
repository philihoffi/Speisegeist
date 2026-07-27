package com.philipphofmann.backend.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.philipphofmann.backend.dto.RecipeDtos.GenerationPreferences;
import com.philipphofmann.backend.dto.RecipeDtos.RecipeSummaryResponse;
import com.philipphofmann.backend.entity.CookingStep;
import com.philipphofmann.backend.entity.DietaryRestriction;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.entity.RecipeIngredient;
import com.philipphofmann.backend.exception.OpenRouterUnavailableException;
import com.philipphofmann.backend.exception.RecipeGenerationException;
import com.philipphofmann.backend.util.StreamingJsonWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Orchestriert die Rezept-Generierung: baut die Prompts auf Basis der Nutzer-
 * präferenzen (Zutaten, Küche, Ernährungseinschränkungen), ruft
 * {@link OpenRouterService} mit Retry/Backoff auf und mappt das KI-JSON auf
 * eine {@link Recipe}-Entität.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeGeneratorService {

    private final OpenRouterService openRouterService;
    private final IngredientService ingredientService;
    private final ObjectMapper objectMapper;

    @Value("${openrouter.api.retry-max-attempts:3}")
    private int maxAttempts;

    @Value("${recipe.batch.max-consecutive-failures:5}")
    private int maxConsecutiveFailures;

    /** Caps how many previously-generated names are fed back into the prompt per batch item. */
    private static final int MAX_AVOID_NAMES = 30;

    private static final String SYSTEM_PROMPT = """
            Du bist ein kreativer Koch-Assistent. Deine Aufgabe ist es, \
            aus den genannten Zutaten ein vollständiges, alltagstaugliches und \
            wirklich leckeres Rezept zu entwickeln.

            ── Pflichtregeln ──────────────────────────────────────────────────
            Ernährungseinschränkungen aus dem Nutzer-Prompt (z. B. Vegan, Glutenfrei, Keto) \
            haben absoluten Vorrang und dürfen unter keinen Umständen verletzt werden. \
            Wenn "Vegan" angegeben ist: keine tierischen Produkte (kein Fleisch, kein Fisch, \
            keine Milch, keine Eier, kein Honig, keine Gelatine) – ersetze nicht-vegane \
            Zutaten durch die beste pflanzliche Alternative und erkläre das im notes-Feld \
            (z. B. "Räuchertofu statt Hähnchen – gibt eine rauchige Tiefe"). \
            Wenn keine Einschränkung angegeben ist, koche frei ohne Vorgaben.
            Die maximale Kochzeit aus dem Nutzer-Prompt darf nicht überschritten werden.

            ── Kreativität & Qualität ─────────────────────────────────────────
            Rezeptname: Wähle einen einladenden, konkreten Namen, der Lust aufs Kochen macht \
            (z. B. "Gerösteter Kürbis-Dhal mit Kokos-Chili" statt "Kürbissuppe"). \
            Vermeide generische Platzhalternamen.
            description: 1–2 lebhafte, appetitliche Sätze. Nenne Hauptaromen, Textur und \
            was das Gericht besonders macht (z. B. "Samtige Kokosbasis trifft auf \
            knusprigen Tempeh – ein vollwertiges Wohlfühlgericht in 30 Minuten.").
            Baue das Rezept um die genannten Zutaten herum. Du darfst sinnvolle \
            Ergänzungen hinzufügen (Aromagemüse, Gewürze, Bindemittel, Säurekick), \
            aber die Kern-Zutaten des Nutzers müssen sichtbar und relevant bleiben.

            ── Präzision & Kochhandwerk ───────────────────────────────────────
            Mengen: Realistisch, metrisch (g, ml, EL, TL, Stück, Prise), auf die \
            gewünschte Portionenzahl skaliert.
            Schritte: Konkret und in Kochlogik-Reihenfolge (Prep → Hitze → Garzeit → Finish). \
            Nenne Temperatur (°C), Zielkonsistenz, Pfannengröße und sensorische Hinweise \
            ("bis die Zwiebeln glasig sind", "bei mittlerer Hitze 3–4 Min. anrösten"). \
            stepNumber beginnt bei 1.
            Zeiten: preparationTimeMinutes (nur Schneiden/Vorbereiten) und \
            cookTimeMinutes (nur aktives Garen) getrennt und realistisch schätzen.
            estimatedKcal: Schätzung pro Portion.

            ── Deutsche Konventionen ──────────────────────────────────────────
            Alle Texte auf Deutsch. Deutsche Zutaten- und Gerichtnamen.
            tags: kurze, kleingeschriebene Schlagworte auf Deutsch \
            (z. B. "schnell", "proteinreich", "einmaltopf"). \
            Wenn eine Küche/Stil angegeben ist, muss diese exakt als Tag enthalten sein \
            (z. B. Küche "Thailändisch" → Tag "thailändisch").

            Optionale Felder ohne sinnvollen Wert auf null setzen. Nährwerte sind Schätzungen.""";

    /**
     * Erzwingt via OpenRouter Structured Outputs, dass die KI ausschließlich JSON
     * im Rezept-Schema liefert (strict = alle Felder required, keine zusätzlichen
     * Properties).
     */
    private static final Map<String, Object> RESPONSE_FORMAT = buildResponseFormat();

    private static Map<String, Object> buildResponseFormat() {
        Map<String, Object> ingredientProps = orderedMap(
                "name",     field("string",  "Deutscher Zutatennamen, z. B. 'Räuchertofu' oder 'Kirschtomaten'"),
                "quantity", nullableField("number",  "Menge in der angegebenen Einheit, z. B. 200 oder 0.5. Null wenn keine sinnvolle Mengenangabe möglich."),
                "unit",     nullableField("string",  "Metrische Einheit: g, ml, EL, TL, Stück, Prise, Bund usw. Null wenn nicht anwendbar."),
                "notes",    nullableField("string",  "Optionale Hinweise zur Zutat, z. B. Ersatz oder Zubereitungshinweis. Null wenn nicht nötig."));

        Map<String, Object> stepProps = orderedMap(
                "stepNumber",      field("integer", "Fortlaufende Schrittnummer, beginnt bei 1."),
                "instruction",     field("string",  "Konkreter Handlungsschritt mit Temperatur, Gardauer und Zielkonsistenz wo relevant."),
                "durationMinutes", nullableField("integer", "Zeitdauer dieses Schritts in Minuten. Null wenn keine sinnvolle Angabe möglich."));

        Map<String, Object> recipeProps = orderedMap(
                "name",                   field("string",  "Einladender, konkreter Rezeptname der Lust aufs Kochen macht."),
                "description",            nullableField("string",  "1-2 appetitliche Sätze: Hauptaromen, Textur und was das Gericht besonders macht."),
                "ingredients",            array(object(ingredientProps), "Vollständige Zutatenliste mit Mengen."),
                "steps",                  array(object(stepProps),      "Zubereitungsschritte in logischer Kochreihenfolge."),
                "preparationTimeMinutes", nullableField("integer", "Reine Vorbereitungszeit in Minuten (Schneiden, Abwiegen usw.), ohne Garzeit."),
                "cookTimeMinutes",        nullableField("integer", "Reine Garzeit in Minuten (Kochen, Backen, Braten), ohne Vorbereitungszeit."),
                "servings",               field("integer", "Anzahl der Portionen auf die alle Mengen skaliert sind."),
                "estimatedKcal",          nullableField("integer", "Geschätzte Kalorien pro Portion. Null wenn keine sinnvolle Schätzung möglich."),
                "tags",                   array(field("string", "Kurzes deutsches Schlagwort in Kleinbuchstaben."), "Schlagworte wie 'schnell', 'proteinreich', 'asiatisch'."));

        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "recipe",
                        "strict", true,
                        "schema", object(recipeProps)));
    }

    private static Map<String, Object> field(String jsonType, String description) {
        return orderedMap("type", jsonType, "description", description);
    }

    private static Map<String, Object> nullableField(String jsonType, String description) {
        return orderedMap("type", List.of(jsonType, "null"), "description", description);
    }

    private static Map<String, Object> array(Map<String, Object> items, String description) {
        return orderedMap("type", "array", "items", items, "description", description);
    }

    private static Map<String, Object> array(Map<String, Object> items) {
        return orderedMap("type", "array", "items", items);
    }

    /** Objekt-Schema im strict-Modus: additionalProperties=false, alle Keys required. */
    private static Map<String, Object> object(Map<String, Object> properties) {
        return orderedMap(
                "type", "object",
                "additionalProperties", false,
                "required", List.copyOf(properties.keySet()),
                "properties", properties);
    }

    /** Like {@link Map#of} but with stable insertion order (needed for {@code required}). */
    private static Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    public Recipe generateRecipe(List<String> ingredients, GenerationPreferences preferences) {
        return generateFromPrompt(buildUserPrompt(ingredients, preferences));
    }

    /**
     * Generates a single recipe for the given free-text theme instead of an explicit
     * ingredient list, used by the batch generation flow. {@code avoidNames} lists recipe
     * names already generated in the same batch so the model avoids repeating itself.
     */
    public Recipe generateRecipeFromTheme(String theme, GenerationPreferences preferences, List<String> avoidNames) {
        return generateFromPrompt(buildThemeUserPrompt(theme, preferences, avoidNames));
    }

    private Recipe generateFromPrompt(String userPrompt) {
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
                String name = i.path("name").asText();
                ingredients.add(RecipeIngredient.builder()
                        .ingredient(ingredientService.resolve(name))
                        .quantity(i.hasNonNull("quantity") ? i.path("quantity").asDouble() : null)
                        .unit(i.hasNonNull("unit") ? i.path("unit").asText() : null)
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
            if (prefs.cuisine() != null) {
                sb.append("\nKüche/Stil: ").append(prefs.cuisine());
                sb.append("\n→ Tag \"").append(prefs.cuisine().toLowerCase()).append("\" muss in den tags enthalten sein.");
            }
            if (prefs.mealType() != null) sb.append("\nMahlzeit: ").append(prefs.mealType());
            if (prefs.cookTime() != null) {
                sb.append("\nMaximale Kochzeit: ").append(prefs.cookTime()).append(" Minuten (nicht überschreiten)");
            }
            if (prefs.servings() != null) {
                sb.append("\nPortionen: ").append(prefs.servings()).append(" (Mengen entsprechend skalieren)");
            }
            if (prefs.dietaryRestrictions() != null && !prefs.dietaryRestrictions().isEmpty()) {
                sb.append("\nErnährungseinschränkungen (PFLICHT, unbedingt einhalten):");
                for (DietaryRestriction r : prefs.dietaryRestrictions()) {
                    sb.append("\n- ").append(r.getDisplayName())
                      .append(": ").append(r.getDescription());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Builds the user prompt for the theme-based batch flow: a free-text direction instead
     * of an explicit ingredient list, plus (if any) the names already generated in this
     * batch so the model picks something different.
     */
    private String buildThemeUserPrompt(String theme, GenerationPreferences prefs, List<String> avoidNames) {
        StringBuilder sb = new StringBuilder("Erfinde ein Rezept zum folgenden Thema: ").append(theme);
        if (prefs != null) {
            if (prefs.cuisine() != null) {
                sb.append("\nKüche/Stil: ").append(prefs.cuisine());
                sb.append("\n→ Tag \"").append(prefs.cuisine().toLowerCase()).append("\" muss in den tags enthalten sein.");
            }
            if (prefs.mealType() != null) sb.append("\nMahlzeit: ").append(prefs.mealType());
            if (prefs.cookTime() != null) {
                sb.append("\nMaximale Kochzeit: ").append(prefs.cookTime()).append(" Minuten (nicht überschreiten)");
            }
            if (prefs.servings() != null) {
                sb.append("\nPortionen: ").append(prefs.servings()).append(" (Mengen entsprechend skalieren)");
            }
            if (prefs.dietaryRestrictions() != null && !prefs.dietaryRestrictions().isEmpty()) {
                sb.append("\nErnährungseinschränkungen (PFLICHT, unbedingt einhalten):");
                for (DietaryRestriction r : prefs.dietaryRestrictions()) {
                    sb.append("\n- ").append(r.getDisplayName())
                      .append(": ").append(r.getDescription());
                }
            }
        }
        if (avoidNames != null && !avoidNames.isEmpty()) {
            sb.append("\nVermeide unbedingt eine Wiederholung dieser bereits in diesem Batch ")
              .append("generierten Rezepte (anderes Gericht, andere Hauptzutat oder andere Zubereitungsart wählen): ")
              .append(String.join("; ", avoidNames));
        }
        return sb.toString();
    }

    public void generateRecipeStreaming(List<String> ingredients, GenerationPreferences preferences,
                                        OutputStream outputStream,
                                        java.util.function.UnaryOperator<Recipe> persister) throws IOException {
        String userPrompt = buildUserPrompt(ingredients, preferences);
        StreamingJsonWriter writer = new StreamingJsonWriter(outputStream, objectMapper);

        ConcurrentLinkedQueue<String> contentBuffer = new ConcurrentLinkedQueue<>();
        RestClientResponseException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                openRouterService.streamComplete(
                        SYSTEM_PROMPT,
                        List.of(OpenRouterService.Message.user(userPrompt)),
                        RESPONSE_FORMAT,
                        new OpenRouterService.StreamEventHandler() {
                            @Override
                            public void onToken(String token) throws IOException {
                                contentBuffer.add(token);
                                // Periodically emit progress events
                                if (contentBuffer.size() % 10 == 0) {
                                    writer.writeRecord("progress", Map.of(
                                            "tokens", contentBuffer.size(),
                                            "content", String.join("", contentBuffer)
                                    ));
                                }
                            }

                            @Override
                            public void onComplete() {
                                String fullContent = String.join("", contentBuffer);
                                try {
                                    Recipe recipe = parseRecipeJson(fullContent);
                                    Recipe saved = persister.apply(recipe);
                                    emitRecipeStreaming(saved, writer);
                                } catch (Exception e) {
                                    log.warn("Failed to parse streamed recipe JSON", e);
                                    try {
                                        writer.writeError("Failed to parse recipe: " + e.getMessage());
                                    } catch (IOException e2) {
                                        log.error("Failed to write error record", e2);
                                    }
                                }
                            }

                            @Override
                            public void onError(String error) {
                                try {
                                    writer.writeError(error);
                                } catch (IOException e) {
                                    log.error("Failed to write error record", e);
                                }
                            }
                        }
                );
                return;
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
            } catch (IOException e) {
                throw e;
            }
        }

        if (lastError != null) {
            throw new OpenRouterUnavailableException(
                    "OpenRouter-Anfrage fehlgeschlagen: " + lastError.getStatusCode(), lastError);
        }
        throw new OpenRouterUnavailableException("OpenRouter nicht erreichbar");
    }

    /**
     * Generates {@code count} recipes for the given theme, streaming one NDJSON
     * {@code "recipe"} event per successfully generated (and persisted) recipe. A single
     * recipe's failure doesn't abort the whole batch — it's reported as a
     * {@code "recipe-error"} event and generation continues — unless
     * {@link #maxConsecutiveFailures} failures happen in a row, which aborts the batch early
     * (protects against burning through paid API calls on a systematically broken
     * theme/prompt or a provider outage). Always ends with a {@code "batch-complete"} summary
     * event with the actually reached counts.
     */
    public void generateThemeBatchStreaming(String theme, int count, GenerationPreferences preferences,
                                            OutputStream outputStream,
                                            java.util.function.UnaryOperator<Recipe> persister) throws IOException {
        StreamingJsonWriter writer = new StreamingJsonWriter(outputStream, objectMapper);
        List<String> avoidNames = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;
        int consecutiveFailures = 0;

        for (int index = 1; index <= count; index++) {
            writer.writeRecord("batch-progress", Map.of("index", index, "total", count));
            try {
                Recipe recipe = generateRecipeFromTheme(theme, preferences, List.copyOf(avoidNames));
                Recipe saved = persister.apply(recipe);
                succeeded++;
                consecutiveFailures = 0;
                avoidNames.add(saved.getName());
                if (avoidNames.size() > MAX_AVOID_NAMES) {
                    avoidNames.remove(0);
                }
                writer.writeRecord("recipe", RecipeSummaryResponse.from(saved));
            } catch (RecipeGenerationException | OpenRouterUnavailableException e) {
                failed++;
                consecutiveFailures++;
                writer.writeRecord("recipe-error", Map.of("index", index, "message",
                        e.getMessage() != null ? e.getMessage() : "Unbekannter Fehler"));
                if (consecutiveFailures >= maxConsecutiveFailures) {
                    log.warn("Batch-Generierung für Thema \"{}\" nach {} aufeinanderfolgenden Fehlschlägen abgebrochen",
                            theme, consecutiveFailures);
                    break;
                }
            }
        }

        writer.writeRecord("batch-complete", Map.of("requested", count, "succeeded", succeeded, "failed", failed));
    }

    private void emitRecipeStreaming(Recipe recipe, StreamingJsonWriter writer) throws IOException {
        if (recipe.getIngredients() != null) {
            for (RecipeIngredient ing : recipe.getIngredients()) {
                Map<String, Object> data = Map.of(
                        "name", ing.getIngredient().getName(),
                        "quantity", ing.getQuantity() != null ? ing.getQuantity() : 0.0,
                        "unit", ing.getUnit() != null ? ing.getUnit() : ""
                );
                writer.writeRecord("ingredient", data);
            }
        }

        if (recipe.getSteps() != null) {
            for (int i = 0; i < recipe.getSteps().size(); i++) {
                CookingStep step = recipe.getSteps().get(i);
                Map<String, Object> data = Map.of(
                        "order", i + 1,
                        "instruction", step.getInstruction(),
                        "duration", step.getDurationMinutes() != null ? step.getDurationMinutes() : 0
                );
                writer.writeRecord("step", data);
            }
        }

        Map<String, Object> completeData = Map.of(
                "id", recipe.getId().toString(),
                "name", recipe.getName(),
                "description", recipe.getDescription() != null ? recipe.getDescription() : "",
                "servings", recipe.getServings(),
                "cookTime", recipe.getCookTimeMinutes() != null ? recipe.getCookTimeMinutes() : 0,
                "preparationTime", recipe.getPreparationTimeMinutes() != null ? recipe.getPreparationTimeMinutes() : 0
        );
        writer.writeRecord("complete", completeData);
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(1000L * (1L << (attempt - 1)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

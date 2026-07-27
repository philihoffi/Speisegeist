package com.philipphofmann.backend.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.philipphofmann.backend.dto.RecipeDtos.GenerationPreferences;
import com.philipphofmann.backend.entity.DietaryRestriction;
import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.exception.OpenRouterUnavailableException;
import com.philipphofmann.backend.exception.RecipeGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeGeneratorServiceTest {

    @Mock private OpenRouterService openRouterService;
    @Mock private IngredientService ingredientService;

    private RecipeGeneratorService service;

    private static final String VALID_RECIPE_JSON = """
            {
              "name": "Tofu-Pfanne",
              "description": "Leckere Tofu-Pfanne",
              "ingredients": [{"name": "Tofu", "quantity": 200, "unit": "g", "notes": null}],
              "steps": [{"stepNumber": 1, "instruction": "Tofu anbraten", "durationMinutes": 10}],
              "preparationTimeMinutes": 5,
              "cookTimeMinutes": 10,
              "servings": 2,
              "estimatedKcal": 350,
              "tags": ["schnell", "vegan"]
            }
            """;

    @BeforeEach
    void setUp() {
        service = new RecipeGeneratorService(openRouterService, ingredientService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "maxConsecutiveFailures", 5);
        // Only used by the tests that reach the JSON-parsing stage.
        lenient().when(openRouterService.getModel()).thenReturn("gpt-4o");
        Ingredient ingredient = Ingredient.builder().name("Tofu").normalizedName("tofu").build();
        lenient().when(ingredientService.resolve(any())).thenReturn(ingredient);
    }

    @Test
    void generateRecipe_returnsRecipeWithCorrectFields() {
        when(openRouterService.complete(any(), any(), any())).thenReturn(VALID_RECIPE_JSON);

        Recipe result = service.generateRecipe(List.of("Tofu"), null);

        assertThat(result.getName()).isEqualTo("Tofu-Pfanne");
        assertThat(result.getServings()).isEqualTo(2);
        assertThat(result.getSourceType()).isEqualTo(Recipe.SourceType.GENERATED);
        assertThat(result.getSourceModel()).isEqualTo("gpt-4o");
        assertThat(result.getTags()).contains("schnell", "vegan");
    }

    @Test
    void generateRecipe_stripsMarkdownCodeFences() {
        String withFences = "```json\n" + VALID_RECIPE_JSON + "\n```";
        when(openRouterService.complete(any(), any(), any())).thenReturn(withFences);

        Recipe result = service.generateRecipe(List.of("Tofu"), null);

        assertThat(result.getName()).isEqualTo("Tofu-Pfanne");
    }

    @Test
    void generateRecipe_retriesOn429AndSucceeds() {
        RestClientResponseException error = mock(RestClientResponseException.class);
        when(error.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        when(openRouterService.complete(any(), any(), any()))
                .thenThrow(error)
                .thenReturn(VALID_RECIPE_JSON);

        Recipe result = service.generateRecipe(List.of("Tofu"), null);

        assertThat(result.getName()).isEqualTo("Tofu-Pfanne");
        verify(openRouterService, times(2)).complete(any(), any(), any());
    }

    @Test
    void generateRecipe_throwsAfterMaxRetries() {
        RestClientResponseException error = mock(RestClientResponseException.class);
        when(error.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        when(openRouterService.complete(any(), any(), any())).thenThrow(error);

        assertThatThrownBy(() -> service.generateRecipe(List.of("Tofu"), null))
                .isInstanceOf(OpenRouterUnavailableException.class);

        verify(openRouterService, times(3)).complete(any(), any(), any());
    }

    @Test
    void generateRecipe_throwsOnInvalidJson() {
        when(openRouterService.complete(any(), any(), any())).thenReturn("not-json");

        assertThatThrownBy(() -> service.generateRecipe(List.of("Tofu"), null))
                .isInstanceOf(RecipeGenerationException.class);
    }

    @Test
    void generateRecipe_throwsOnIncompleteJson() {
        when(openRouterService.complete(any(), any(), any())).thenReturn("{\"name\": \"X\"}");

        assertThatThrownBy(() -> service.generateRecipe(List.of("Tofu"), null))
                .isInstanceOf(RecipeGenerationException.class);
    }

    @Test
    void generateRecipe_withPreferences_includesCuisineInPrompt() {
        when(openRouterService.complete(any(), any(), any())).thenReturn(VALID_RECIPE_JSON);
        GenerationPreferences prefs = new GenerationPreferences("Asiatisch", null, 30, 2, null);

        service.generateRecipe(List.of("Tofu"), prefs);

        verify(openRouterService).complete(any(), argThat(messages ->
                messages.stream().anyMatch(m -> m.content().contains("Asiatisch"))
        ), any());
    }

    @Test
    void generateRecipe_withDietaryRestrictions_includesInPrompt() {
        when(openRouterService.complete(any(), any(), any())).thenReturn(VALID_RECIPE_JSON);
        GenerationPreferences prefs = new GenerationPreferences(null, null, null, null,
                Set.of(DietaryRestriction.VEGAN));

        service.generateRecipe(List.of("Tofu"), prefs);

        verify(openRouterService).complete(any(), argThat(messages ->
                messages.stream().anyMatch(m -> m.content().contains("Vegan"))
        ), any());
    }

    // ---------- theme-based batch generation ----------

    @Test
    void generateRecipeFromTheme_includesThemeAndAvoidNamesInPrompt() {
        when(openRouterService.complete(any(), any(), any())).thenReturn(VALID_RECIPE_JSON);

        service.generateRecipeFromTheme("Schnelle vegane Feierabend-Gerichte", null,
                List.of("Tofu-Curry", "Linsen-Dahl"));

        verify(openRouterService).complete(any(), argThat(messages ->
                messages.stream().anyMatch(m -> m.content().contains("Schnelle vegane Feierabend-Gerichte")
                        && m.content().contains("Tofu-Curry")
                        && m.content().contains("Linsen-Dahl"))
        ), any());
    }

    @Test
    void generateRecipeFromTheme_omitsAvoidHintWhenNoNamesYet() {
        when(openRouterService.complete(any(), any(), any())).thenReturn(VALID_RECIPE_JSON);

        service.generateRecipeFromTheme("Herbstgerichte", null, List.of());

        verify(openRouterService).complete(any(), argThat(messages ->
                messages.stream().noneMatch(m -> m.content().contains("Vermeide"))
        ), any());
    }

    @Test
    void generateThemeBatchStreaming_emitsOneRecipeEventPerSuccess() throws Exception {
        when(openRouterService.complete(any(), any(), any())).thenReturn(VALID_RECIPE_JSON);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        service.generateThemeBatchStreaming("Herbstgerichte", 3, null, out, recipe -> recipe);

        List<JsonNode> events = parseNdjson(out);
        assertThat(eventsOfType(events, "recipe")).hasSize(3);
        assertThat(eventsOfType(events, "recipe-error")).isEmpty();
        JsonNode complete = eventsOfType(events, "batch-complete").get(0);
        assertThat(complete.path("data").path("requested").asInt()).isEqualTo(3);
        assertThat(complete.path("data").path("succeeded").asInt()).isEqualTo(3);
        assertThat(complete.path("data").path("failed").asInt()).isEqualTo(0);
    }

    @Test
    void generateThemeBatchStreaming_continuesAfterASingleFailure() throws Exception {
        when(openRouterService.complete(any(), any(), any()))
                .thenReturn(VALID_RECIPE_JSON)
                .thenReturn("not-json")
                .thenReturn(VALID_RECIPE_JSON);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        service.generateThemeBatchStreaming("Herbstgerichte", 3, null, out, recipe -> recipe);

        List<JsonNode> events = parseNdjson(out);
        assertThat(eventsOfType(events, "recipe")).hasSize(2);
        assertThat(eventsOfType(events, "recipe-error")).hasSize(1);
        JsonNode complete = eventsOfType(events, "batch-complete").get(0);
        assertThat(complete.path("data").path("succeeded").asInt()).isEqualTo(2);
        assertThat(complete.path("data").path("failed").asInt()).isEqualTo(1);
    }

    @Test
    void generateThemeBatchStreaming_abortsAfterConsecutiveFailureThreshold() throws Exception {
        ReflectionTestUtils.setField(service, "maxConsecutiveFailures", 2);
        when(openRouterService.complete(any(), any(), any())).thenReturn("not-json");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        service.generateThemeBatchStreaming("Herbstgerichte", 10, null, out, recipe -> recipe);

        List<JsonNode> events = parseNdjson(out);
        assertThat(eventsOfType(events, "recipe-error")).hasSize(2);
        assertThat(eventsOfType(events, "recipe")).isEmpty();
        verify(openRouterService, times(2)).complete(any(), any(), any());
        JsonNode complete = eventsOfType(events, "batch-complete").get(0);
        assertThat(complete.path("data").path("requested").asInt()).isEqualTo(10);
        assertThat(complete.path("data").path("succeeded").asInt()).isEqualTo(0);
        assertThat(complete.path("data").path("failed").asInt()).isEqualTo(2);
    }

    private List<JsonNode> parseNdjson(ByteArrayOutputStream out) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> events = new ArrayList<>();
        for (String line : out.toString().split("\n")) {
            if (!line.isBlank()) {
                events.add(mapper.readTree(line));
            }
        }
        return events;
    }

    private List<JsonNode> eventsOfType(List<JsonNode> events, String type) {
        return events.stream().filter(e -> type.equals(e.path("type").asText())).toList();
    }
}

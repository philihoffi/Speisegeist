package com.philipphofmann.backend.service;

import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.entity.RecipeImage;
import com.philipphofmann.backend.entity.RecipeIngredient;
import com.philipphofmann.backend.exception.RecipeNotFoundException;
import com.philipphofmann.backend.repository.RecipeImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeImageServiceTest {

    @Mock private RecipeService recipeService;
    @Mock private RecipeImageRepository recipeImageRepository;
    @Mock private OpenRouterService openRouterService;

    @InjectMocks private RecipeImageService service;

    private UUID recipeId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "imageSize", "1024x1024");
        recipeId = UUID.randomUUID();
    }

    private RecipeIngredient ing(String name) {
        return RecipeIngredient.builder()
                .ingredient(Ingredient.builder().name(name).normalizedName(name.toLowerCase()).build())
                .build();
    }

    private Recipe recipe(String name, String description, RecipeIngredient... ingredients) {
        return Recipe.builder()
                .id(recipeId)
                .name(name)
                .description(description)
                .ingredients(List.of(ingredients))
                .build();
    }

    private void stubGeneration() {
        when(openRouterService.generateImage(any(), any(), any()))
                .thenReturn(new OpenRouterService.GeneratedImage(new byte[]{7}, MediaType.IMAGE_PNG_VALUE));
        when(recipeImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private String capturePrompt() {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(openRouterService).generateImage(prompt.capture(), any(), any());
        return prompt.getValue();
    }

    @Test
    void returnsCachedImageWithoutCallingOpenRouter() {
        RecipeImage cached = RecipeImage.builder().recipeId(recipeId).build();
        when(recipeService.getRecipe(recipeId)).thenReturn(recipe("Pasta", null));
        when(recipeImageRepository.findById(recipeId)).thenReturn(Optional.of(cached));

        RecipeImage result = service.getOrCreateImage(recipeId);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(openRouterService);
        verify(recipeImageRepository, never()).save(any());
    }

    @Test
    void generatesAndPersistsImageOnFirstAccess() {
        when(recipeService.getRecipe(recipeId)).thenReturn(recipe("Pasta", "Cremig"));
        when(recipeImageRepository.findById(recipeId)).thenReturn(Optional.empty());
        stubGeneration();
        when(openRouterService.getImageModel()).thenReturn("openai/dall-e-3");

        RecipeImage result = service.getOrCreateImage(recipeId);

        assertThat(result.getRecipeId()).isEqualTo(recipeId);
        assertThat(result.getData()).containsExactly(7);
        assertThat(result.getSourceModel()).isEqualTo("openai/dall-e-3");
    }

    @Test
    void promptContainsRecipeNameAndDescription() {
        when(recipeService.getRecipe(recipeId)).thenReturn(recipe("Kürbis-Dhal", "Samtig und würzig"));
        when(recipeImageRepository.findById(recipeId)).thenReturn(Optional.empty());
        stubGeneration();

        service.getOrCreateImage(recipeId);

        assertThat(capturePrompt())
                .contains("Kürbis-Dhal")
                .contains("Samtig und würzig");
    }

    @Test
    void promptListsAtMostFourIngredients() {
        when(recipeService.getRecipe(recipeId)).thenReturn(recipe("Eintopf", null,
                ing("Karotte"), ing("Sellerie"), ing("Lauch"), ing("Kartoffel"), ing("Petersilie")));
        when(recipeImageRepository.findById(recipeId)).thenReturn(Optional.empty());
        stubGeneration();

        service.getOrCreateImage(recipeId);

        String prompt = capturePrompt();
        assertThat(prompt).contains("Karotte", "Sellerie", "Lauch", "Kartoffel");
        // The 5th ingredient must be dropped so the prompt stays focused.
        assertThat(prompt).doesNotContain("Petersilie");
    }

    @Test
    void promptOmitsDescriptionWhenBlank() {
        when(recipeService.getRecipe(recipeId)).thenReturn(recipe("Suppe", "   "));
        when(recipeImageRepository.findById(recipeId)).thenReturn(Optional.empty());
        stubGeneration();

        service.getOrCreateImage(recipeId);

        assertThat(capturePrompt()).contains("Suppe").doesNotContain("\"Suppe\".    ");
    }

    @Test
    void promptForbidsTextAndWatermarks() {
        when(recipeService.getRecipe(recipeId)).thenReturn(recipe("Salat", null));
        when(recipeImageRepository.findById(recipeId)).thenReturn(Optional.empty());
        stubGeneration();

        service.getOrCreateImage(recipeId);

        assertThat(capturePrompt()).contains("No text, no watermarks");
    }

    @Test
    void propagatesNotFoundForUnknownRecipe() {
        when(recipeService.getRecipe(recipeId)).thenThrow(new RecipeNotFoundException(recipeId));

        assertThatThrownBy(() -> service.getOrCreateImage(recipeId))
                .isInstanceOf(RecipeNotFoundException.class);

        verifyNoInteractions(openRouterService);
        verify(recipeImageRepository, never()).save(any());
    }
}

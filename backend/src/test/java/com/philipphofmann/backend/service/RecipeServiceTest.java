package com.philipphofmann.backend.service;

import com.philipphofmann.backend.dto.RecipeDtos.*;
import com.philipphofmann.backend.entity.CookingStep;
import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.entity.RecipeIngredient;
import com.philipphofmann.backend.exception.RecipeNotFoundException;
import com.philipphofmann.backend.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(recipeService, "appVersion", "1.0");
    }


    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private RecipeGeneratorService recipeGeneratorService;

    @Mock
    private IngredientService ingredientService;

    @InjectMocks
    private RecipeService recipeService;

    @Test
    void generateRecipe_setsSourceVersion() {
        Recipe generated = Recipe.builder().name("X").build();
        when(recipeGeneratorService.generateRecipe(anyList(), any())).thenReturn(generated);
        when(recipeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Recipe saved = recipeService.generateRecipe(new RecipeGenerationRequest(List.of("Tofu"), null));

        assertNotNull(saved.getSourceVersion());
        verify(recipeRepository).save(generated);
    }

    @Test
    void updateRecipe_preservesUnprovidedFields() {
        Recipe existing = Recipe.builder()
                .name("Old").description("d").servings(2)
                .ingredients(List.of(RecipeIngredient.builder()
                        .ingredient(Ingredient.builder().name("A").build()).build()))
                .steps(List.of(CookingStep.builder().instruction("s").build()))
                .tags(Set.of("vegan"))
                .build();
        when(recipeRepository.findById(any())).thenReturn(Optional.of(existing));
        when(recipeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Only the name is provided (simulates a partial inline edit)
        RecipeRequest patch = new RecipeRequest("New", null, null, null, null, null, null, null, null);
        Recipe updated = recipeService.updateRecipe(UUID.randomUUID(), patch);

        assertEquals("New", updated.getName());
        assertEquals("d", updated.getDescription());
        assertEquals(2, updated.getServings());
        assertEquals(1, updated.getIngredients().size());
        assertEquals(1, updated.getSteps().size());
        assertEquals(Set.of("vegan"), updated.getTags());
    }

    @Test
    void updateRecipe_fullRequestReplacesCollections() {
        Recipe existing = Recipe.builder().name("Old").tags(Set.of("vegan")).build();
        when(recipeRepository.findById(any())).thenReturn(Optional.of(existing));
        when(recipeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ingredientService.resolve(any()))
                .thenAnswer(i -> Ingredient.builder().name(i.getArgument(0)).build());

        RecipeRequest full = new RecipeRequest("New", "desc",
                List.of(new IngredientDto("Tomate", 2.0, "Stück", null)),
                List.of(new StepDto(1, "Kochen", 5)),
                10, 20, 4, 500, Set.of("schnell"));
        Recipe updated = recipeService.updateRecipe(UUID.randomUUID(), full);

        assertEquals(1, updated.getIngredients().size());
        assertEquals(1, updated.getSteps().size());
        assertEquals(Set.of("schnell"), updated.getTags());
        assertEquals(4, updated.getServings());
    }

    @Test
    void createRecipe_manualRequestBuildsCompleteEntity() {
        when(recipeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ingredientService.resolve(any()))
                .thenAnswer(i -> Ingredient.builder().name(i.getArgument(0)).build());

        RecipeRequest request = new RecipeRequest("Tofu-Pfanne", "Schnell",
                List.of(new IngredientDto("Tofu", 200.0, "g", null)),
                List.of(new StepDto(1, "Anbraten", 5)),
                null, 15, 2, 450, Set.of("vegan"));

        Recipe created = recipeService.createRecipe(request);

        assertEquals(Recipe.SourceType.MANUAL, created.getSourceType());
        assertEquals(1, created.getIngredients().size());
        assertEquals(1, created.getSteps().size());
        assertEquals(Set.of("vegan"), created.getTags());
    }

    @Test
    void getRecipe_throwsWhenNotFound() {
        when(recipeRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(RecipeNotFoundException.class, () -> recipeService.getRecipe(UUID.randomUUID()));
    }

    @Test
    void searchRecipes_withTermDelegatesToSearchQuery() {
        when(recipeRepository.findBySearch(eq("tofu"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        recipeService.searchRecipes("tofu", null, 0, 12);
        verify(recipeRepository).findBySearch(eq("tofu"), any());
    }

    @Test
    void searchRecipes_withTagDelegatesToTagQuery() {
        when(recipeRepository.findByTag(eq("vegan"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        recipeService.searchRecipes(null, "vegan", 0, 12);
        verify(recipeRepository).findByTag(eq("vegan"), any());
    }
}

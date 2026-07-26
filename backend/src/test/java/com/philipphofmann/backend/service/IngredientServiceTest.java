package com.philipphofmann.backend.service;

import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.exception.IngredientNotFoundException;
import com.philipphofmann.backend.repository.IngredientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private IngredientService ingredientService;

    // --- normalize() ---

    @ParameterizedTest(name = "''{0}'' → ''{1}''")
    @CsvSource({
            "Tomate,tomat",
            "Tomaten,tomat",
            "Bio Tomate,tomat",
            "frische Tomaten,tomat",
            "  Knoblauch  ,knoblauch",
            "Karotten,karott",
            "Ei,ei",
    })
    void normalize_stripsFillerAndPlurals(String input, String expected) {
        assertEquals(expected, IngredientService.normalize(input));
    }

    @Test
    void normalize_nullOrBlankReturnsEmpty() {
        assertEquals("", IngredientService.normalize(null));
        assertEquals("", IngredientService.normalize("   "));
    }

    // --- resolve() ---

    @Test
    void resolve_exactMatchReturnsExisting() {
        Ingredient existing = Ingredient.builder().name("Tofu").normalizedName("tofu").build();
        when(ingredientRepository.findByNormalizedName("tofu")).thenReturn(Optional.of(existing));

        Ingredient result = ingredientService.resolve("Tofu");

        assertSame(existing, result);
        verify(ingredientRepository, never()).save(any());
    }

    @Test
    void resolve_similarMatchDeduplicates() {
        Ingredient existing = Ingredient.builder().name("Tomate").normalizedName("tomat").build();
        when(ingredientRepository.findByNormalizedName(anyString())).thenReturn(Optional.empty());
        when(ingredientRepository.findSimilarNormalized(anyString(), anyFloat()))
                .thenReturn(List.of(existing));

        Ingredient result = ingredientService.resolve("Tomaten");

        assertSame(existing, result);
        verify(ingredientRepository, never()).save(any());
    }

    @Test
    void resolve_newIngredientIsSaved() {
        Ingredient saved = Ingredient.builder().name("Ingwer").normalizedName("ingwer").build();
        when(ingredientRepository.findByNormalizedName(anyString())).thenReturn(Optional.empty());
        when(ingredientRepository.findSimilarNormalized(anyString(), anyFloat())).thenReturn(List.of());
        when(ingredientRepository.save(any())).thenReturn(saved);

        Ingredient result = ingredientService.resolve("Ingwer");

        assertSame(saved, result);
        verify(ingredientRepository).save(argThat(i -> "ingwer".equals(i.getNormalizedName())));
    }

    // --- getIngredient() ---

    @Test
    void getIngredient_throwsWhenNotFound() {
        when(ingredientRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(IngredientNotFoundException.class,
                () -> ingredientService.getIngredient(UUID.randomUUID()));
    }

    // --- deleteIngredient() ---

    @Test
    void deleteIngredient_throwsWhenNotFound() {
        when(ingredientRepository.existsById(any())).thenReturn(false);
        assertThrows(IngredientNotFoundException.class,
                () -> ingredientService.deleteIngredient(UUID.randomUUID()));
    }

    @Test
    void deleteIngredient_deletesWhenFound() {
        UUID id = UUID.randomUUID();
        when(ingredientRepository.existsById(id)).thenReturn(true);

        ingredientService.deleteIngredient(id);

        verify(ingredientRepository).deleteById(id);
    }
}

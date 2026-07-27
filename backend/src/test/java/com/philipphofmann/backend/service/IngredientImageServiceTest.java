package com.philipphofmann.backend.service;

import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.entity.IngredientImage;
import com.philipphofmann.backend.exception.IngredientNotFoundException;
import com.philipphofmann.backend.repository.IngredientImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngredientImageServiceTest {

    @Mock private IngredientService ingredientService;
    @Mock private IngredientImageRepository ingredientImageRepository;
    @Mock private OpenRouterService openRouterService;

    @InjectMocks private IngredientImageService service;

    private UUID ingredientId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "imageSize", "1024x1024");
        ReflectionTestUtils.setField(service, "imageQuality", "medium");
        ingredientId = UUID.randomUUID();
    }

    private Ingredient ingredient(String name) {
        return Ingredient.builder().id(ingredientId).name(name).normalizedName(name.toLowerCase()).build();
    }

    @Test
    void returnsCachedImageWithoutCallingOpenRouter() {
        IngredientImage cached = IngredientImage.builder().ingredientId(ingredientId).build();
        when(ingredientImageRepository.findById(ingredientId)).thenReturn(Optional.of(cached));

        IngredientImage result = service.getOrCreateImage(ingredientId);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(openRouterService);
        verify(ingredientImageRepository, never()).save(any());
    }

    @Test
    void generatesAndPersistsImageOnFirstAccess() {
        when(ingredientImageRepository.findById(ingredientId)).thenReturn(Optional.empty());
        when(ingredientService.getIngredient(ingredientId)).thenReturn(ingredient("Tofu"));
        when(openRouterService.generateImage(any(), eq("1024x1024"), eq("medium"), eq(1)))
                .thenReturn(new OpenRouterService.GeneratedImage(new byte[]{1, 2}, MediaType.IMAGE_PNG_VALUE));
        when(openRouterService.getImageModel()).thenReturn("openai/dall-e-3");
        when(ingredientImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IngredientImage result = service.getOrCreateImage(ingredientId);

        assertThat(result.getIngredientId()).isEqualTo(ingredientId);
        assertThat(result.getData()).containsExactly(1, 2);
        assertThat(result.getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertThat(result.getSourceModel()).isEqualTo("openai/dall-e-3");
        verify(ingredientImageRepository).save(any());
    }

    @Test
    void promptContainsIngredientNameAndForbidsExtras() {
        when(ingredientImageRepository.findById(ingredientId)).thenReturn(Optional.empty());
        when(ingredientService.getIngredient(ingredientId)).thenReturn(ingredient("Kurkuma"));
        when(openRouterService.generateImage(any(), any(), any(), any()))
                .thenReturn(new OpenRouterService.GeneratedImage(new byte[]{1}, MediaType.IMAGE_PNG_VALUE));
        when(ingredientImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.getOrCreateImage(ingredientId);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(openRouterService).generateImage(prompt.capture(), any(), any(), any());
        assertThat(prompt.getValue()).contains("Kurkuma");
        assertThat(prompt.getValue()).contains("KEINE weiteren Zutaten");
    }

    @Test
    void promptIsPersistedAlongsideTheImage() {
        when(ingredientImageRepository.findById(ingredientId)).thenReturn(Optional.empty());
        when(ingredientService.getIngredient(ingredientId)).thenReturn(ingredient("Basilikum"));
        when(openRouterService.generateImage(any(), any(), any(), any()))
                .thenReturn(new OpenRouterService.GeneratedImage(new byte[]{9}, MediaType.IMAGE_PNG_VALUE));
        when(ingredientImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IngredientImage result = service.getOrCreateImage(ingredientId);

        assertThat(result.getPrompt()).contains("Basilikum");
    }

    @Test
    void propagatesNotFoundForUnknownIngredient() {
        when(ingredientImageRepository.findById(ingredientId)).thenReturn(Optional.empty());
        when(ingredientService.getIngredient(ingredientId))
                .thenThrow(new IngredientNotFoundException(ingredientId));

        assertThatThrownBy(() -> service.getOrCreateImage(ingredientId))
                .isInstanceOf(IngredientNotFoundException.class);

        verify(ingredientImageRepository, never()).save(any());
    }
}

package com.philipphofmann.backend.controller;

import com.philipphofmann.backend.dto.RecipeDtos.PageResponse;
import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.exception.GlobalExceptionHandler;
import com.philipphofmann.backend.service.IngredientImageService;
import com.philipphofmann.backend.service.IngredientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class IngredientControllerTest {

    @Mock private IngredientService ingredientService;
    @Mock private IngredientImageService ingredientImageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new IngredientController(ingredientService, ingredientImageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returnsPageWithIngredients() throws Exception {
        Ingredient tofu = Ingredient.builder().name("Tofu").normalizedName("tofu").build();
        when(ingredientService.listIngredients(any(), any()))
                .thenReturn(new PageImpl<>(List.of(tofu)));

        mockMvc.perform(get("/ingredients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Tofu"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_withSearchParam_passesSearchToService() throws Exception {
        when(ingredientService.listIngredients(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/ingredients").param("search", "tofu"))
                .andExpect(status().isOk());

        verify(ingredientService).listIngredients(eq("tofu"), any());
    }

    @Test
    void get_returnsIngredient() throws Exception {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = Ingredient.builder().id(id).name("Karotte").normalizedName("karott").build();
        when(ingredientService.getIngredient(id)).thenReturn(ingredient);

        mockMvc.perform(get("/ingredients/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Karotte"));
    }

    @Test
    void create_returns201WithCreatedIngredient() throws Exception {
        Ingredient created = Ingredient.builder().name("Spinat").normalizedName("spinat").build();
        when(ingredientService.createIngredient(any())).thenReturn(created);

        mockMvc.perform(post("/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Spinat\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Spinat"));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns200WithUpdatedIngredient() throws Exception {
        UUID id = UUID.randomUUID();
        Ingredient updated = Ingredient.builder().id(id).name("Brokkoli").normalizedName("brokk").build();
        when(ingredientService.updateIngredient(any(), any())).thenReturn(updated);

        mockMvc.perform(put("/ingredients/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Brokkoli\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Brokkoli"));
    }

    @Test
    void delete_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/ingredients/" + id))
                .andExpect(status().isNoContent());

        verify(ingredientService).deleteIngredient(id);
    }
}

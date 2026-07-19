package com.philipphofmann.backend.controller;

import com.philipphofmann.backend.config.JwtAuthenticationFilter.AuthenticatedUser;
import com.philipphofmann.backend.dto.RecipeDtos.PageResponse;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.exception.GlobalExceptionHandler;
import com.philipphofmann.backend.exception.RecipeNotFoundException;
import com.philipphofmann.backend.service.RecipeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeControllerTest {

    @Mock
    private RecipeService recipeService;

    private MockMvc mockMvc;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RecipeController(recipeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        AuthenticatedUser principal = new AuthenticatedUser(userId, "a@b.com");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void search_returnsOkWithPage() throws Exception {
        when(recipeService.searchRecipes(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 1, 0));

        mockMvc.perform(MockMvcRequestBuilders.get("/recipes").param("search", "tofu"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content").isArray());
    }

    @Test
    void getRecipe_notFound_returns404() throws Exception {
        when(recipeService.getRecipe(any(), any())).thenThrow(new RecipeNotFoundException(UUID.randomUUID()));

        mockMvc.perform(MockMvcRequestBuilders.get("/recipes/" + UUID.randomUUID()))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void getRecipe_found_returns200() throws Exception {
        Recipe recipe = Recipe.builder().name("Test").userId(userId).build();
        when(recipeService.getRecipe(any(), any())).thenReturn(recipe);

        mockMvc.perform(MockMvcRequestBuilders.get("/recipes/" + UUID.randomUUID()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Test"));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/recipes/" + UUID.randomUUID()))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
    }

    @Test
    void create_returns201() throws Exception {
        Recipe recipe = Recipe.builder().name("Manuell").userId(userId).build();
        when(recipeService.createRecipe(any(), any())).thenReturn(recipe);

        mockMvc.perform(MockMvcRequestBuilders.post("/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Manuell\",\"ingredients\":[{\"name\":\"Tofu\"}],\"steps\":[{\"stepNumber\":1,\"instruction\":\"Anbraten\"}]}"))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Manuell"));
    }

    @Test
    void generate_returns201() throws Exception {
        Recipe recipe = Recipe.builder().name("Gen").userId(userId).build();
        when(recipeService.generateRecipe(any(), any())).thenReturn(recipe);

        mockMvc.perform(MockMvcRequestBuilders.post("/recipes/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredients\":[\"Tofu\"]}"))
                .andExpect(MockMvcResultMatchers.status().isCreated());
    }

    @Test
    void rate_returns200() throws Exception {
        Recipe recipe = Recipe.builder().name("Gen").userId(userId).build();
        when(recipeService.rateRecipe(any(), any(), anyDouble())).thenReturn(recipe);

        mockMvc.perform(MockMvcRequestBuilders.post("/recipes/" + UUID.randomUUID() + "/rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4.5}"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }
}

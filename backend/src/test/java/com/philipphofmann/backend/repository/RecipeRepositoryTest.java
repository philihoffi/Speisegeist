package com.philipphofmann.backend.repository;

import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.entity.Recipe;
import com.philipphofmann.backend.entity.RecipeIngredient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Rollback
class RecipeRepositoryTest {

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private EntityManager em;

    @Test
    void findBySearch_findsByIngredientName_caseInsensitive() {
        Ingredient tofu = Ingredient.builder().name("Tofu").normalizedName("tofu").build();
        em.persist(tofu);
        Recipe recipe = Recipe.builder()
                .name("Gemüsepfanne")
                .ingredients(List.of(RecipeIngredient.builder().ingredient(tofu).build()))
                .build();
        em.persist(recipe);
        em.flush();
        em.clear();

        Page<Recipe> result = recipeRepository.findBySearch("tofu", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void findBySearch_findsByName_caseInsensitive() {
        em.persist(Recipe.builder().name("Tofu-Pfanne").build());
        em.flush();
        em.clear();

        Page<Recipe> result = recipeRepository.findBySearch("tofu", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void findByTag_findsByExactTag() {
        em.persist(Recipe.builder().name("X").tags(Set.of("vegan")).build());
        em.flush();
        em.clear();

        Page<Recipe> result = recipeRepository.findByTag("vegan", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }
}

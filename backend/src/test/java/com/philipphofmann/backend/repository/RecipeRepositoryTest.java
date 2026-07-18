package com.philipphofmann.backend.repository;

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
import java.util.UUID;

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
    void findByUserIdAndSearch_findsByIngredientName_caseInsensitive() {
        UUID userId = UUID.randomUUID();
        Recipe recipe = Recipe.builder()
                .userId(userId)
                .name("Gemüsepfanne")
                .ingredients(List.of(RecipeIngredient.builder().name("Tofu").build()))
                .build();
        em.persist(recipe);
        em.flush();
        em.clear();

        Page<Recipe> result = recipeRepository.findByUserIdAndSearch(userId, "tofu", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void findByUserIdAndSearch_ignoresOtherUsers() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        em.persist(Recipe.builder().userId(owner).name("Mein").build());
        em.persist(Recipe.builder().userId(other).name("Tofu").build());
        em.flush();
        em.clear();

        Page<Recipe> result = recipeRepository.findByUserIdAndSearch(owner, "tofu", PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void findByUserIdAndTag_findsByExactTag() {
        UUID userId = UUID.randomUUID();
        em.persist(Recipe.builder().userId(userId).name("X").tags(Set.of("vegan")).build());
        em.flush();
        em.clear();

        Page<Recipe> result = recipeRepository.findByUserIdAndTag(userId, "vegan", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }
}

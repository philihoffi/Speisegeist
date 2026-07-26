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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
@Rollback
class RecipeRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("speisegeist")
            .withUsername("speisegeist")
            .withPassword("speisegeist");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        // Flyway aus test-properties überschreiben
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private EntityManager em;

    @Test
    void findBySearch_findsByIngredientName_caseInsensitive() {
        Ingredient tofu = Ingredient.builder().name("Tofu").normalizedName("tofu").build();
        em.persist(tofu);
        em.persist(Recipe.builder()
                .name("Gemüsepfanne")
                .ingredients(List.of(RecipeIngredient.builder().ingredient(tofu).build()))
                .build());
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

        Page<Recipe> result = recipeRepository.findBySearch("TOFU", PageRequest.of(0, 10));

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

    @Test
    void findByTag_doesNotMatchDifferentTag() {
        em.persist(Recipe.builder().name("Y").tags(Set.of("vegetarisch")).build());
        em.flush();
        em.clear();

        Page<Recipe> result = recipeRepository.findByTag("vegan", PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void findBySearch_returnsEmptyForNoMatch() {
        em.persist(Recipe.builder().name("Pasta").build());
        em.flush();
        em.clear();

        Page<Recipe> result = recipeRepository.findBySearch("schnitzel", PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
    }
}

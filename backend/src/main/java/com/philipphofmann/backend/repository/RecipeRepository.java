package com.philipphofmann.backend.repository;

import com.philipphofmann.backend.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for recipes.
 */
@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    /** Searches recipes by name, ingredient name, or tag. */
    @Query("SELECT DISTINCT r FROM Recipe r WHERE "
            + "LOWER(r.name) LIKE LOWER(CONCAT('%', ?1, '%')) "
            + "OR EXISTS (SELECT i FROM r.ingredients i WHERE LOWER(i.ingredient.name) LIKE LOWER(CONCAT('%', ?1, '%'))) "
            + "OR ?1 MEMBER OF r.tags")
    Page<Recipe> findBySearch(String search, Pageable pageable);

    /** Returns recipes filtered by a single tag. */
    @Query("SELECT r FROM Recipe r WHERE ?1 MEMBER OF r.tags")
    Page<Recipe> findByTag(String tag, Pageable pageable);

    /** Returns recipes filtered by tag AND matching a search term (name or ingredient). */
    @Query("SELECT DISTINCT r FROM Recipe r WHERE ?1 MEMBER OF r.tags AND ("
            + "LOWER(r.name) LIKE LOWER(CONCAT('%', ?2, '%')) "
            + "OR EXISTS (SELECT i FROM r.ingredients i WHERE LOWER(i.ingredient.name) LIKE LOWER(CONCAT('%', ?2, '%'))))")
    Page<Recipe> findByTagAndSearch(String tag, String search, Pageable pageable);
}

package com.philipphofmann.backend.repository;

import com.philipphofmann.backend.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for recipes, scoped per user.
 */
@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    /** Returns all recipes owned by the given user. */
    List<Recipe> findByUserId(UUID userId);

    /** Returns a single recipe only if it belongs to the given user. */
    Optional<Recipe> findByIdAndUserId(UUID id, UUID userId);

    /** Searches the user's recipes by name, ingredient name, or tag. */
    @Query("SELECT DISTINCT r FROM Recipe r WHERE r.userId = ?1 AND ("
            + "LOWER(r.name) LIKE LOWER(CONCAT('%', ?2, '%')) "
            + "OR EXISTS (SELECT i FROM r.ingredients i WHERE LOWER(i.ingredient.name) LIKE LOWER(CONCAT('%', ?2, '%'))) "
            + "OR ?2 MEMBER OF r.tags)")
    Page<Recipe> findByUserIdAndSearch(UUID userId, String search, Pageable pageable);

    /** Returns the user's recipes filtered by a single tag. */
    @Query("SELECT r FROM Recipe r WHERE r.userId = ?1 AND ?2 MEMBER OF r.tags")
    Page<Recipe> findByUserIdAndTag(UUID userId, String tag, Pageable pageable);

    /** Returns a page of the user's recipes ordered newest-first. */
    Page<Recipe> findByUserId(UUID userId, Pageable pageable);
}

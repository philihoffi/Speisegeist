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

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    List<Recipe> findByUserId(UUID userId);

    Optional<Recipe> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT DISTINCT r FROM Recipe r WHERE r.userId = ?1 AND ("
            + "LOWER(r.name) LIKE LOWER(CONCAT('%', ?2, '%')) "
            + "OR EXISTS (SELECT i FROM r.ingredients i WHERE LOWER(i.ingredient.name) LIKE LOWER(CONCAT('%', ?2, '%'))) "
            + "OR ?2 MEMBER OF r.tags)")
    Page<Recipe> findByUserIdAndSearch(UUID userId, String search, Pageable pageable);

    @Query("SELECT r FROM Recipe r WHERE r.userId = ?1 AND ?2 MEMBER OF r.tags")
    Page<Recipe> findByUserIdAndTag(UUID userId, String tag, Pageable pageable);

    Page<Recipe> findByUserId(UUID userId, Pageable pageable);
}

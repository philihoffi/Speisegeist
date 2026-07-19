package com.philipphofmann.backend.repository;

import com.philipphofmann.backend.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for the global {@link Ingredient} catalog.
 */
@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

    /** Finds a catalog ingredient regardless of letter case. */
    Optional<Ingredient> findByNameIgnoreCase(String name);
}

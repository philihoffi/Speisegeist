package com.philipphofmann.backend.repository;

import com.philipphofmann.backend.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

    /** Findet eine Katalog-Zutat unabhängig von der Groß-/Kleinschreibung. */
    Optional<Ingredient> findByNameIgnoreCase(String name);
}

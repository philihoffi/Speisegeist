package com.philipphofmann.backend.repository;

import com.philipphofmann.backend.entity.IngredientImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for persisted ingredient images.
 */
@Repository
public interface IngredientImageRepository extends JpaRepository<IngredientImage, UUID> {
}
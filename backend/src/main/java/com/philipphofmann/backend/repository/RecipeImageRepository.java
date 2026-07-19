package com.philipphofmann.backend.repository;

import com.philipphofmann.backend.entity.RecipeImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for persisted recipe images.
 */
@Repository
public interface RecipeImageRepository extends JpaRepository<RecipeImage, UUID> {
}

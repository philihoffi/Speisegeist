package com.philipphofmann.backend.repository;

import com.philipphofmann.backend.entity.RecipeImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RecipeImageRepository extends JpaRepository<RecipeImage, UUID> {
}

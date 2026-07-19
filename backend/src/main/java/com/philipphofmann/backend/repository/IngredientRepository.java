package com.philipphofmann.backend.repository;

import com.philipphofmann.backend.entity.Ingredient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for the global {@link Ingredient} catalog.
 */
@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

    /** Finds a catalog ingredient regardless of letter case. */
    Optional<Ingredient> findByNameIgnoreCase(String name);

    /** Returns all ingredients ordered by name ascending, paged. */
    Page<Ingredient> findAllByOrderByNameAsc(Pageable pageable);

    /** Returns ingredients whose name contains the term (case-insensitive), ordered by name. */
    Page<Ingredient> findByNameContainingIgnoreCaseOrderByNameAsc(String name, Pageable pageable);

    /** Returns ingredients of a given warengruppe (case-insensitive), ordered by name. */
    Page<Ingredient> findByWarengruppeIgnoreCaseOrderByNameAsc(String warengruppe, Pageable pageable);

    /** Returns all distinct warengruppen present in the catalog, ordered alphabetically. */
    @Query("select distinct i.warengruppe from Ingredient i where i.warengruppe is not null order by i.warengruppe")
    List<String> findDistinctWarengruppen();
}

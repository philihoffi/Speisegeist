package com.philipphofmann.backend.service;

import com.philipphofmann.backend.dto.IngredientDtos.IngredientRequest;
import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.exception.IngredientNotFoundException;
import com.philipphofmann.backend.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Maintains the global {@link Ingredient} catalog. Its main responsibility is
 * {@link #resolve(String, String)}: find an ingredient by name or create it, so
 * the catalog gradually collects every ingredient that appears in recipes.
 * Additionally it exposes CRUD operations for the management UI.
 *
 * <p>Duplicates are prevented by normalising ingredient names before persisting
 * and comparing them against the {@code normalized_name} unique column. A
 * PostgreSQL {@code pg_trgm} similarity fallback catches near-duplicates
 * ("Tomaten" → "Tomate").
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientService {

    private final IngredientRepository ingredientRepository;

    private static final Set<String> FILLER_PREFIXES = Set.of(
            "bio ", "frische ", "frischer ", "frisches ", "reife ", "reifer ",
            "rohe ", "gekochte ", "gebratene ", "gehackte ", "geschnittene ",
            "ganze ", "kleine ", "große ", "feine "
    );

    private static final Set<String> FILLER_SUFFIXES = Set.of(
            " frisch", " getrocknet", " reif", " roh", " gekocht", " gebraten",
            " gehackt", " geschnitten", " ganz", " klein", " groß", " fein",
            " bio"
    );

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PLURAL_EN = Pattern.compile("(?<!e)en$");
    private static final Pattern PLURAL_N = Pattern.compile("(?<![aeiou])n$");
    private static final Pattern PLURAL_E = Pattern.compile("(?<![aeiouöäü])e$");

    /**
     * Normalises a raw ingredient name into a deduplication key. The result is
     * deterministic: the same conceptual ingredient should produce the same
     * normalised string regardless of minor wording differences.
     */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.trim().toLowerCase();
        s = WHITESPACE.matcher(s).replaceAll(" ");

        for (String prefix : FILLER_PREFIXES) {
            if (s.startsWith(prefix)) {
                s = s.substring(prefix.length());
            }
        }

        for (String suffix : FILLER_SUFFIXES) {
            if (s.endsWith(suffix) && s.length() > suffix.length()) {
                s = s.substring(0, s.length() - suffix.length());
            }
        }

        s = s.trim();
        if (s.length() > 3) {
            s = PLURAL_EN.matcher(s).replaceAll("");
        }
        if (s.length() > 3) {
            s = PLURAL_N.matcher(s).replaceAll("");
        }
        if (s.length() > 3) {
            s = PLURAL_E.matcher(s).replaceAll("");
        }


        return s.trim();
    }

    /**
     * Returns the catalog ingredient for the name or creates it. Uses name
     * normalisation and pg_trgm similarity to avoid near-duplicates.
     */
    @Transactional
    public Ingredient resolve(String rawName, String warengruppe) {
        String name = rawName == null ? "" : rawName.trim();
        String normalized = normalize(name);
        String normalizedWarengruppe = normalizeWarengruppe(warengruppe);

        var exact = ingredientRepository.findByNormalizedName(normalized);
        if (exact.isPresent()) {
            Ingredient existing = exact.get();
            if (existing.getWarengruppe() == null && normalizedWarengruppe != null) {
                existing.setWarengruppe(normalizedWarengruppe);
            }
            return existing;
        }

        var similar = ingredientRepository.findSimilarNormalized(normalized, 0.72f);
        if (!similar.isEmpty()) {
            Ingredient best = similar.get(0);
            log.debug("Dedup '{}' → existierende '{}' (sim={})", name, best.getName(),
                    similarity(best.getNormalizedName(), normalized));
            if (best.getWarengruppe() == null && normalizedWarengruppe != null) {
                best.setWarengruppe(normalizedWarengruppe);
            }
            return best;
        }

        return ingredientRepository.save(Ingredient.builder()
                .name(name)
                .normalizedName(normalized)
                .warengruppe(normalizedWarengruppe)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<Ingredient> listIngredients(String search, String warengruppe, Pageable pageable) {
        if (warengruppe != null && !warengruppe.isBlank()) {
            return ingredientRepository.findByWarengruppeIgnoreCaseOrderByNameAsc(warengruppe, pageable);
        }
        if (search != null && !search.isBlank()) {
            return ingredientRepository.findByNameContainingIgnoreCaseOrderByNameAsc(search, pageable);
        }
        return ingredientRepository.findAllByOrderByNameAsc(pageable);
    }

    @Transactional(readOnly = true)
    public List<String> listWarengruppen() {
        return ingredientRepository.findDistinctWarengruppen();
    }

    @Transactional(readOnly = true)
    public Ingredient getIngredient(UUID id) {
        return ingredientRepository.findById(id)
                .orElseThrow(() -> new IngredientNotFoundException(id));
    }

    @Transactional
    public Ingredient createIngredient(IngredientRequest request) {
        String name = request.name().trim();
        String normalized = normalize(name);
        String wg = normalizeWarengruppe(request.warengruppe());

        List<Ingredient> similar = ingredientRepository.findSimilarNormalized(normalized, 0.72f);
        if (!similar.isEmpty()) {
            Ingredient best = similar.get(0);
            log.debug("Dedup create '{}' → existierende '{}' (sim={})",
                    name, best.getName(), similarity(best.getNormalizedName(), normalized));
            return best;
        }

        Ingredient ingredient = Ingredient.builder()
                .name(name)
                .normalizedName(normalized)
                .warengruppe(wg)
                .build();
        return ingredientRepository.save(ingredient);
    }

    @Transactional
    public Ingredient updateIngredient(UUID id, IngredientRequest request) {
        Ingredient existing = getIngredient(id);
        String name = request.name().trim();
        existing.setName(name);
        existing.setNormalizedName(normalize(name));
        existing.setWarengruppe(normalizeWarengruppe(request.warengruppe()));
        return ingredientRepository.save(existing);
    }

    @Transactional
    public void deleteIngredient(UUID id) {
        if (!ingredientRepository.existsById(id)) {
            throw new IngredientNotFoundException(id);
        }
        ingredientRepository.deleteById(id);
    }

    private static String normalizeWarengruppe(String warengruppe) {
        return (warengruppe == null || warengruppe.isBlank()) ? null : warengruppe.trim();
    }

    private static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int maxLen = Math.max(a.length(), b.length());
        int dist = levenshtein(a, b);
        return 1.0 - ((double) dist / maxLen);
    }

    private static int levenshtein(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        }
        return d[a.length()][b.length()];
    }
}
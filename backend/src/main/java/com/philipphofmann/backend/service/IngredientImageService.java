package com.philipphofmann.backend.service;

import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.entity.IngredientImage;
import com.philipphofmann.backend.repository.IngredientImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Generates and caches AI images for catalog ingredients. The first time an
 * ingredient's image is requested, a prompt is built from the ingredient's
 * name and warengruppe and sent to {@link OpenRouterService}. Subsequent
 * requests serve the persisted image directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientImageService {

    private final IngredientService ingredientService;
    private final IngredientImageRepository ingredientImageRepository;
    private final OpenRouterService openRouterService;

    @Value("${openrouter.image-size:1024x1024}")
    private String imageSize;

    /**
     * Returns the (possibly newly generated) image for the given ingredient.
     * Not transactional: the OpenRouter call may be slow and should not hold
     * a DB connection.
     */
    public IngredientImage getOrCreateImage(UUID ingredientId) {
        return ingredientImageRepository.findById(ingredientId)
                .orElseGet(() -> {
                    Ingredient ingredient = ingredientService.getIngredient(ingredientId);
                    return generateAndStore(ingredient);
                });
    }

    private IngredientImage generateAndStore(Ingredient ingredient) {
        String prompt = buildPrompt(ingredient);
        log.debug("Generiere Bild für Zutat {} ({})", ingredient.getId(), ingredient.getName());

        OpenRouterService.GeneratedImage generated = openRouterService.generateImage(prompt, imageSize, 1);

        IngredientImage image = IngredientImage.builder()
                .ingredientId(ingredient.getId())
                .data(generated.data())
                .contentType(generated.mediaType())
                .prompt(prompt)
                .sourceModel(openRouterService.getImageModel())
                .build();

        return ingredientImageRepository.save(image);
    }

    private static final String SCENE_GUIDE =
            "Wähle die passende Anordnung basierend auf der Art der Zutat:\n"
            + "- Gewürzpulver / gemahlene Gewürze (Salz, Pfeffer, Kurkuma, Zimt, Curry, Paprika …):"
            + " locker auf dem hellen Holzbrett verstreut, ein Teil in einem kleinen Keramikmörser"
            + " oder auf einem Holzlöffel; warmes Seitenlicht betont Farbe und Körnung.\n"
            + "- Frische Kräuter (Basilikum, Rosmarin, Minze, Petersilie, Koriander …):"
            + " locker gebündelt auf dem Holzbrett, sichtbare Wassertropfen auf den Blättern.\n"
            + "- Öle & flüssige Fette (Olivenöl, Kokosöl, Butter, Ghee …):"
            + " in einem kleinen Glasfläschchen oder einer Keramikschale auf dem Holzbrett,"
            + " Licht lässt die Flüssigkeit schimmern.\n"
            + "- Mehle & feine Pulver (Mehl, Stärke, Kakao, Backpulver, Hefeflocken …):"
            + " in einer kleinen Keramikschale, Holzlöffel daneben,"
            + " diffuses Licht macht die feine Textur sichtbar.\n"
            + "- Nüsse & Samen (Mandeln, Walnüsse, Sesam, Chiasamen, Kürbiskerne …):"
            + " locker verstreut, einige aufgebrochen um die innere Textur zu zeigen.\n"
            + "- Hülsenfrüchte (Linsen, Kichererbsen, Bohnen …):"
            + " locker verstreut, ein Teil in einer kleinen Schale daneben.\n"
            + "- Gemüse, Obst, Tofu und alle anderen Zutaten: frisch und einzeln auf dem Holzbrett.";

    private String buildPrompt(Ingredient ingredient) {
        return new StringBuilder("Fotorealistisches, appetitliches Food-Foto der einzelnen Zutat \"")
                .append(ingredient.getName()).append("\".")
                .append("\n\nDie Zutat soll pur, unverarbeitet und einzeln auf einem hellen Holzbrett")
                .append(" vor dunklem Holzhintergrund fotografiert sein. Natürliches Licht,")
                .append(" 45-Grad-Winkel, hohe Detailschärfe.")
                .append(" KEINE weiteren Zutaten, Gewürze oder Garnierungen hinzufügen.")
                .append(" Nur genau diese eine Zutat zeigen.\n\n")
                .append(SCENE_GUIDE)
                .toString();
    }
}
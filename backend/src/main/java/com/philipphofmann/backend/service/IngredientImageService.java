package com.philipphofmann.backend.service;

import com.philipphofmann.backend.entity.Ingredient;
import com.philipphofmann.backend.entity.IngredientImage;
import com.philipphofmann.backend.repository.IngredientImageRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Generates and caches AI images for catalog ingredients. The first time an
 * ingredient's image is requested, a prompt is built from the ingredient's
 * name and warengruppe and sent to {@link OpenRouterService}. Subsequent
 * requests serve the persisted image directly.
 */
@Service
public class IngredientImageService extends AbstractImageGenerationService<Ingredient, IngredientImage> {

    private final IngredientService ingredientService;

    public IngredientImageService(IngredientService ingredientService, IngredientImageRepository ingredientImageRepository,
                                   OpenRouterService openRouterService) {
        super(ingredientImageRepository, openRouterService);
        this.ingredientService = ingredientService;
    }

    @Override
    protected Ingredient fetchEntity(UUID id) {
        return ingredientService.getIngredient(id);
    }

    @Override
    protected String describeEntity(Ingredient ingredient) {
        return "Zutat " + ingredient.getId() + " (" + ingredient.getName() + ")";
    }

    @Override
    protected IngredientImage newImage(Ingredient ingredient, OpenRouterService.GeneratedImage generated, String prompt, String sourceModel) {
        return IngredientImage.builder()
                .ingredientId(ingredient.getId())
                .data(generated.data())
                .contentType(generated.mediaType())
                .prompt(prompt)
                .sourceModel(sourceModel)
                .build();
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

    @Override
    protected String buildPrompt(Ingredient ingredient) {
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

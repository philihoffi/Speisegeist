-- Globaler Zutaten-Katalog (Stammdaten): jede Zutat existiert genau einmal.
CREATE TABLE ingredients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    warengruppe VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Namen global eindeutig (case-insensitive), damit "Tofu" und "tofu" derselbe Eintrag sind.
CREATE UNIQUE INDEX ux_ingredients_name_lower ON ingredients (LOWER(name));
CREATE INDEX idx_ingredients_warengruppe ON ingredients (warengruppe);

-- Bestehende, bisher pro Rezept eingebettete Zutaten in den Katalog übernehmen.
-- Ein Eintrag je distinktem Namen (case-insensitive); eine gesetzte Warengruppe wird bevorzugt.
INSERT INTO ingredients (name, warengruppe)
SELECT DISTINCT ON (LOWER(name)) name, warengruppe
FROM recipe_ingredients
ORDER BY LOWER(name), (warengruppe IS NULL), warengruppe;

-- recipe_ingredients normalisieren: Fremdschlüssel statt eingebettetem Namen/Warengruppe.
ALTER TABLE recipe_ingredients ADD COLUMN ingredient_id UUID;

UPDATE recipe_ingredients ri
SET ingredient_id = i.id
FROM ingredients i
WHERE LOWER(ri.name) = LOWER(i.name);

ALTER TABLE recipe_ingredients ALTER COLUMN ingredient_id SET NOT NULL;
ALTER TABLE recipe_ingredients
    ADD CONSTRAINT fk_recipe_ingredients_ingredient
    FOREIGN KEY (ingredient_id) REFERENCES ingredients (id);

-- Alte, jetzt in den Katalog ausgelagerte Spalten entfernen.
DROP INDEX IF EXISTS idx_recipe_ingredients_warengruppe;
ALTER TABLE recipe_ingredients DROP COLUMN warengruppe;
ALTER TABLE recipe_ingredients DROP COLUMN name;

CREATE INDEX idx_recipe_ingredients_ingredient_id ON recipe_ingredients (ingredient_id);

-- Deduplizierung: normalisierter Name (ohne Füllwörter, singularisiert)
-- plus pg_trgm für Fuzzy-Vergleiche, damit "Tomaten" → "tomate" → matcht existierende "Tomate".
ALTER TABLE ingredients ADD COLUMN normalized_name VARCHAR(255);
UPDATE ingredients SET normalized_name = LOWER(name);
ALTER TABLE ingredients ALTER COLUMN normalized_name SET NOT NULL;

-- Alten case-insensitive Index droppen, neuen auf normalisiertem Namen anlegen.
DROP INDEX IF EXISTS ux_ingredients_name_lower;
CREATE UNIQUE INDEX ux_ingredients_normalized_name ON ingredients (normalized_name);

-- pg_trgm für Fuzzy-Suche (similarity).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_ingredients_normalized_trgm ON ingredients USING gin (normalized_name gin_trgm_ops);
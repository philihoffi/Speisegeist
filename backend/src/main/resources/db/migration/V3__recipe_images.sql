-- KI-generierte Rezeptbilder (1:1 zu recipes). Eigene Tabelle, damit die Bild-Bytes
-- nicht bei jeder Rezept-Abfrage mitgeladen werden.
CREATE TABLE recipe_images (
    recipe_id UUID PRIMARY KEY REFERENCES recipes(id) ON DELETE CASCADE,
    data BYTEA NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    prompt TEXT,
    source_model VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

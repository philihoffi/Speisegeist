-- KI-generierte Zutatenbilder (1:1 zu ingredients). Eigene Tabelle, damit die DDL-Bytes
-- nicht bei jeder Zutaten-Abfrage mitgeladen werden.
CREATE TABLE ingredient_images (
    ingredient_id UUID PRIMARY KEY REFERENCES ingredients(id) ON DELETE CASCADE,
    data BYTEA NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    prompt TEXT,
    source_model VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
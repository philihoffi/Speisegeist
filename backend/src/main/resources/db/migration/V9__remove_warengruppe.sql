DROP INDEX IF EXISTS idx_ingredients_warengruppe;
ALTER TABLE ingredients DROP COLUMN IF EXISTS warengruppe;

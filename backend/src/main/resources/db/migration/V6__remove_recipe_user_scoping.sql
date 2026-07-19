ALTER TABLE recipes DROP CONSTRAINT IF EXISTS recipes_user_id_fkey;
DROP INDEX IF EXISTS idx_recipes_user_id;
ALTER TABLE recipes DROP COLUMN IF EXISTS user_id;
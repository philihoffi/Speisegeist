-- Create Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

-- Create Recipes Table
CREATE TABLE recipes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    preparation_time_minutes INT,
    cook_time_minutes INT,
    servings INT DEFAULT 1,
    estimated_kcal INT,
    rating DOUBLE PRECISION,
    source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    source_model VARCHAR(100),
    source_version VARCHAR(50),
    generated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create Recipe Tags Table
CREATE TABLE recipe_tags (
    recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (recipe_id, tag)
);

-- Create Recipe Ingredients Table
CREATE TABLE recipe_ingredients (
    recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    display_order INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    quantity DOUBLE PRECISION,
    unit VARCHAR(50),
    warengruppe VARCHAR(100),
    notes TEXT,
    PRIMARY KEY (recipe_id, display_order)
);

-- Create Cooking Steps Table
CREATE TABLE cooking_steps (
    recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    step_number INT NOT NULL,
    instruction TEXT NOT NULL,
    duration_minutes INT,
    PRIMARY KEY (recipe_id, step_number)
);

-- Create Indices
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_recipes_user_id ON recipes(user_id);
CREATE INDEX idx_recipes_name ON recipes(name);
CREATE INDEX idx_recipes_created_at ON recipes(created_at DESC);
CREATE INDEX idx_recipe_ingredients_warengruppe ON recipe_ingredients(warengruppe);
CREATE INDEX idx_recipe_tags_tag ON recipe_tags(tag);

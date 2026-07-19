-- Replace native enum type with varchar + check constraint so Hibernate's @Enumerated(STRING) works.
ALTER TABLE users ALTER COLUMN role DROP DEFAULT;
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(20);
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'));
DROP TYPE IF EXISTS user_role;
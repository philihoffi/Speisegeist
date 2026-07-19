-- Add role column for admin/users distinction.
CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');
ALTER TABLE users ADD COLUMN role user_role NOT NULL DEFAULT 'USER';
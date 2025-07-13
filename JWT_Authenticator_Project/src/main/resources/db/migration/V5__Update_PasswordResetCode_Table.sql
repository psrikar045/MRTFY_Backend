-- Rename brand_id column to user_id in password_reset_codes table
ALTER TABLE password_reset_codes RENAME COLUMN brand_id TO user_id;

-- Create index on user_id and email for faster lookups
CREATE INDEX idx_password_reset_codes_user_id_email ON password_reset_codes(user_id, email);
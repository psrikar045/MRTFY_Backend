-- Add isBrandClaimed column to brands table
ALTER TABLE brands ADD COLUMN is_brand_claimed BOOLEAN NOT NULL DEFAULT FALSE;

-- Create index for better query performance
CREATE INDEX idx_brand_is_claimed ON brands(is_brand_claimed);

COMMENT ON COLUMN brands.is_brand_claimed IS 'Flag indicating whether the brand has been claimed by a user';
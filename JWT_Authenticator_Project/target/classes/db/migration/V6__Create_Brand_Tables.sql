-- Brand Data Extraction System Database Schema
-- Version: 1.0
-- Created: 2025-07-12

-- Main brands table
CREATE TABLE brands (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    website VARCHAR(500) NOT NULL UNIQUE,
    description TEXT,
    industry VARCHAR(100),
    location VARCHAR(100),
    founded VARCHAR(50),
    company_type VARCHAR(100),
    employees VARCHAR(50),
    extraction_time_seconds DECIMAL(10,3),
    last_extraction_timestamp TIMESTAMP,
    extraction_message TEXT,
    freshness_score INTEGER DEFAULT 100,
    needs_update BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Brand assets table (logos, icons, banners, etc.)
CREATE TABLE brand_assets (
    id BIGSERIAL PRIMARY KEY,
    brand_id BIGINT NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    asset_type VARCHAR(20) NOT NULL CHECK (asset_type IN ('LOGO', 'SYMBOL', 'ICON', 'BANNER', 'IMAGE')),
    original_url TEXT NOT NULL,
    stored_path TEXT,
    file_name VARCHAR(255),
    file_size BIGINT,
    mime_type VARCHAR(100),
    download_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (download_status IN ('PENDING', 'DOWNLOADING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    download_error TEXT,
    download_attempts INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    downloaded_at TIMESTAMP
);

-- Brand colors table
CREATE TABLE brand_colors (
    id BIGSERIAL PRIMARY KEY,
    brand_id BIGINT NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    hex_code VARCHAR(7) NOT NULL,
    rgb_value VARCHAR(20),
    brightness INTEGER,
    color_name VARCHAR(100),
    usage_context VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Brand fonts table
CREATE TABLE brand_fonts (
    id BIGSERIAL PRIMARY KEY,
    brand_id BIGINT NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    font_name VARCHAR(100) NOT NULL,
    font_type VARCHAR(50),
    font_stack TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Brand social links table
CREATE TABLE brand_social_links (
    id BIGSERIAL PRIMARY KEY,
    brand_id BIGINT NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('TWITTER', 'LINKEDIN', 'FACEBOOK', 'YOUTUBE', 'INSTAGRAM', 'TIKTOK', 'OTHER')),
    url TEXT NOT NULL,
    extraction_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Brand images table
CREATE TABLE brand_images (
    id BIGSERIAL PRIMARY KEY,
    brand_id BIGINT NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    source_url TEXT NOT NULL,
    alt_text TEXT,
    stored_path TEXT,
    file_name VARCHAR(255),
    file_size BIGINT,
    mime_type VARCHAR(100),
    download_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (download_status IN ('PENDING', 'DOWNLOADING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    download_error TEXT,
    download_attempts INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    downloaded_at TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_brand_name ON brands(name);
CREATE INDEX idx_brand_website ON brands(website);
CREATE INDEX idx_brand_created ON brands(created_at);
CREATE INDEX idx_brand_freshness ON brands(freshness_score);
CREATE INDEX idx_brand_needs_update ON brands(needs_update);

CREATE INDEX idx_asset_type ON brand_assets(asset_type);
CREATE INDEX idx_asset_brand ON brand_assets(brand_id);
CREATE INDEX idx_asset_download_status ON brand_assets(download_status);

CREATE INDEX idx_color_brand ON brand_colors(brand_id);
CREATE INDEX idx_color_hex ON brand_colors(hex_code);

CREATE INDEX idx_font_brand ON brand_fonts(brand_id);
CREATE INDEX idx_font_name ON brand_fonts(font_name);

CREATE INDEX idx_social_brand ON brand_social_links(brand_id);
CREATE INDEX idx_social_platform ON brand_social_links(platform);

CREATE INDEX idx_image_brand ON brand_images(brand_id);
CREATE INDEX idx_image_download_status ON brand_images(download_status);

-- Create updated_at trigger for brands table
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_brands_updated_at 
    BEFORE UPDATE ON brands 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Insert some sample data for testing (optional)
-- This will be populated automatically when the forward service extracts brand data

COMMENT ON TABLE brands IS 'Main table storing brand/company information extracted from websites';
COMMENT ON TABLE brand_assets IS 'Brand assets like logos, icons, banners with download tracking';
COMMENT ON TABLE brand_colors IS 'Brand color palette information';
COMMENT ON TABLE brand_fonts IS 'Brand typography information';
COMMENT ON TABLE brand_social_links IS 'Brand social media presence';
COMMENT ON TABLE brand_images IS 'Additional brand images with download tracking';
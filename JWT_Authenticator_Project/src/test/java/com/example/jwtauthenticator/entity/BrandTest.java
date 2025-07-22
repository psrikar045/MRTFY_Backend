package com.example.jwtauthenticator.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Brand Entity Tests")
public class BrandTest {

    private Brand brand;

    @BeforeEach
    void setUp() {
        brand = new Brand();
    }

    @Nested
    @DisplayName("Brand Claimed Status Tests")
    class BrandClaimedTests {

        @Test
        @DisplayName("Should have default unclaimed status")
        public void testDefaultBrandClaimedStatus() {
            // Test default value
            assertFalse(brand.getIsBrandClaimed(), "Brand should be unclaimed by default");
        }

        @Test
        @DisplayName("Should set brand as claimed")
        public void testSetBrandAsClaimed() {
            // Test setter for claimed status
            brand.setIsBrandClaimed(true);
            assertTrue(brand.getIsBrandClaimed(), "Brand should be marked as claimed");
        }

        @Test
        @DisplayName("Should set brand as unclaimed")
        public void testSetBrandAsUnclaimed() {
            // First set as claimed
            brand.setIsBrandClaimed(true);
            assertTrue(brand.getIsBrandClaimed());
            
            // Then set as unclaimed
            brand.setIsBrandClaimed(false);
            assertFalse(brand.getIsBrandClaimed(), "Brand should be marked as unclaimed");
        }

        @Test
        @DisplayName("Should handle multiple status changes")
        public void testMultipleStatusChanges() {
            // Test multiple changes
            brand.setIsBrandClaimed(true);
            assertTrue(brand.getIsBrandClaimed());
            
            brand.setIsBrandClaimed(false);
            assertFalse(brand.getIsBrandClaimed());
            
            brand.setIsBrandClaimed(true);
            assertTrue(brand.getIsBrandClaimed());
        }
    }

    @Nested
    @DisplayName("Brand Properties Tests")
    class BrandPropertiesTests {

        @Test
        @DisplayName("Should set and get brand name")
        public void testBrandName() {
            String brandName = "Test Brand";
            brand.setName(brandName);
            assertEquals(brandName, brand.getName(), "Brand name should match the set value");
        }

        @Test
        @DisplayName("Should set and get website")
        public void testWebsite() {
            String website = "testbrand.com";
            brand.setWebsite(website);
            assertEquals(website, brand.getWebsite(), "Website should match the set value");
        }

        @Test
        @DisplayName("Should set and get industry")
        public void testIndustry() {
            String industry = "Technology";
            brand.setIndustry(industry);
            assertEquals(industry, brand.getIndustry(), "Industry should match the set value");
        }

        @Test
        @DisplayName("Should set and get description")
        public void testDescription() {
            String description = "This is a test brand description";
            brand.setDescription(description);
            assertEquals(description, brand.getDescription(), "Description should match the set value");
        }

        @Test
        @DisplayName("Should set and get location")
        public void testLocation() {
            String location = "San Francisco, CA";
            brand.setLocation(location);
            assertEquals(location, brand.getLocation(), "Location should match the set value");
        }

        @Test
        @DisplayName("Should set and get founded year")
        public void testFounded() {
            String founded = "2020";
            brand.setFounded(founded);
            assertEquals(founded, brand.getFounded(), "Founded year should match the set value");
        }

        @Test
        @DisplayName("Should handle null values gracefully")
        public void testNullValues() {
            brand.setName(null);
            brand.setWebsite(null);
            brand.setIndustry(null);
            brand.setDescription(null);
            brand.setLocation(null);
            brand.setFounded(null);
            
            assertNull(brand.getName(), "Brand name should be null");
            assertNull(brand.getWebsite(), "Website should be null");
            assertNull(brand.getIndustry(), "Industry should be null");
            assertNull(brand.getDescription(), "Description should be null");
            assertNull(brand.getLocation(), "Location should be null");
            assertNull(brand.getFounded(), "Founded should be null");
        }

        @Test
        @DisplayName("Should handle empty strings")
        public void testEmptyStrings() {
            brand.setName("");
            brand.setWebsite("");
            brand.setIndustry("");
            brand.setDescription("");
            brand.setLocation("");
            brand.setFounded("");
            
            assertEquals("", brand.getName(), "Brand name should be empty string");
            assertEquals("", brand.getWebsite(), "Website should be empty string");
            assertEquals("", brand.getIndustry(), "Industry should be empty string");
            assertEquals("", brand.getDescription(), "Description should be empty string");
            assertEquals("", brand.getLocation(), "Location should be empty string");
            assertEquals("", brand.getFounded(), "Founded should be empty string");
        }
    }

    @Nested
    @DisplayName("Brand Builder Tests")
    class BrandBuilderTests {

        @Test
        @DisplayName("Should create brand using builder pattern")
        public void testBrandBuilder() {
            Brand builtBrand = Brand.builder()
                    .name("Built Brand")
                    .website("builtbrand.com")
                    .industry("Technology")
                    .description("Built brand description")
                    .location("San Francisco")
                    .founded("2020")
                    .isBrandClaimed(true)
                    .build();

            assertEquals("Built Brand", builtBrand.getName());
            assertEquals("builtbrand.com", builtBrand.getWebsite());
            assertEquals("Technology", builtBrand.getIndustry());
            assertEquals("Built brand description", builtBrand.getDescription());
            assertEquals("San Francisco", builtBrand.getLocation());
            assertEquals("2020", builtBrand.getFounded());
            assertTrue(builtBrand.getIsBrandClaimed());
        }

        @Test
        @DisplayName("Should create brand with minimal builder")
        public void testMinimalBrandBuilder() {
            Brand minimalBrand = Brand.builder()
                    .name("Minimal Brand")
                    .website("minimal.com")
                    .build();

            assertEquals("Minimal Brand", minimalBrand.getName());
            assertEquals("minimal.com", minimalBrand.getWebsite());
            assertNull(minimalBrand.getIndustry());
            assertNull(minimalBrand.getDescription());
            assertNull(minimalBrand.getLocation());
            assertNull(minimalBrand.getFounded());
            // Default value should be false
            assertFalse(minimalBrand.getIsBrandClaimed());
        }

        @Test
        @DisplayName("Should create brand with default values")
        public void testBrandBuilderDefaults() {
            Brand defaultBrand = Brand.builder()
                    .name("Default Brand")
                    .website("default.com")
                    .build();

            // Test default values
            assertFalse(defaultBrand.getIsBrandClaimed());
            assertFalse(defaultBrand.getNeedsUpdate());
            assertEquals(100, defaultBrand.getFreshnessScore());
            assertNotNull(defaultBrand.getAssets());
            assertNotNull(defaultBrand.getColors());
            assertNotNull(defaultBrand.getFonts());
            assertNotNull(defaultBrand.getSocialLinks());
            assertNotNull(defaultBrand.getImages());
        }
    }

    @Test
    @DisplayName("Should create brand with all properties")
    public void testCompleteBrand() {
        String brandName = "Complete Test Brand";
        String website = "completetest.com";
        String industry = "Technology";
        String description = "A complete test brand with all properties";
        String location = "New York, NY";
        String founded = "2019";
        boolean isClaimed = true;

        brand.setName(brandName);
        brand.setWebsite(website);
        brand.setIndustry(industry);
        brand.setDescription(description);
        brand.setLocation(location);
        brand.setFounded(founded);
        brand.setIsBrandClaimed(isClaimed);

        assertEquals(brandName, brand.getName());
        assertEquals(website, brand.getWebsite());
        assertEquals(industry, brand.getIndustry());
        assertEquals(description, brand.getDescription());
        assertEquals(location, brand.getLocation());
        assertEquals(founded, brand.getFounded());
        assertEquals(isClaimed, brand.getIsBrandClaimed());
    }

    @Nested
    @DisplayName("Brand Relationship Tests")
    class BrandRelationshipTests {

        @Test
        @DisplayName("Should manage brand assets")
        public void testBrandAssets() {
            assertNotNull(brand.getAssets());
            assertTrue(brand.getAssets().isEmpty());
            
            // Test that collections are initialized
            assertEquals(0, brand.getAssets().size());
            assertEquals(0, brand.getColors().size());
            assertEquals(0, brand.getFonts().size());
            assertEquals(0, brand.getSocialLinks().size());
            assertEquals(0, brand.getImages().size());
        }

        @Test
        @DisplayName("Should handle freshness scoring")
        public void testFreshnessScoring() {
            // Test default freshness score
            assertEquals(100, brand.getFreshnessScore());
            assertFalse(brand.getNeedsUpdate());
            
            // Test setting freshness score
            brand.setFreshnessScore(75);
            assertEquals(75, brand.getFreshnessScore());
            
            brand.setNeedsUpdate(true);
            assertTrue(brand.getNeedsUpdate());
        }

        @Test
        @DisplayName("Should handle category assignments")
        public void testCategoryAssignments() {
            Long categoryId = 1L;
            Long subCategoryId = 2L;
            
            brand.setCategoryId(categoryId);
            brand.setSubCategoryId(subCategoryId);
            
            assertEquals(categoryId, brand.getCategoryId());
            assertEquals(subCategoryId, brand.getSubCategoryId());
        }
    }
}
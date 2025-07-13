package com.example.jwtauthenticator.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BrandTest {

    @Test
    public void testIsBrandClaimedGetterAndSetter() {
        Brand brand = new Brand();
        
        // Test default value
        assertFalse(brand.getIsBrandClaimed());
        
        // Test setter
        brand.setIsBrandClaimed(true);
        assertTrue(brand.getIsBrandClaimed());
    }
}
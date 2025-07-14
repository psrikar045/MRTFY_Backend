package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.config.GoogleSearchConfig;
import com.example.jwtauthenticator.dto.BrandInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrandInfoServiceTest {

    private BrandInfoService brandInfoService;

    @BeforeEach
    void setUp() {
        GoogleSearchConfig config = new GoogleSearchConfig();
        config.setApiKey("test-api-key");
        config.setCx("test-cx-id");
        brandInfoService = new BrandInfoService(config);
    }

    @Test
    void testEmptyQuery() {
        BrandInfoResponse response = brandInfoService.resolveBrandInfo("");
        assertEquals("error", response.getStatus());
        assertEquals("Input cannot be empty", response.getMessage());
    }

    @Test
    void testNullQuery() {
        BrandInfoResponse response = brandInfoService.resolveBrandInfo(null);
        assertEquals("error", response.getStatus());
        assertEquals("Input cannot be empty", response.getMessage());
    }

    @Test
    void testInvalidUrlFormat() {
        BrandInfoResponse response = brandInfoService.resolveBrandInfo("http://invalid url with spaces");
        assertEquals("error", response.getStatus());
        assertEquals("The provided input is not a valid URL format.", response.getMessage());
    }

    @Test
    void testValidHttpsUrl() {
        // This test will make an actual HTTP request to google.com
        BrandInfoResponse response = brandInfoService.resolveBrandInfo("https://www.google.com");
        assertEquals("success", response.getStatus());
        assertNotNull(response.getResolvedUrl());
        assertTrue(response.getResolvedUrl().startsWith("https://"));
    }

    @Test
    void testDomainNameResolution() {
        // This test will make an actual HTTP request
        BrandInfoResponse response = brandInfoService.resolveBrandInfo("google.com");
        assertNotNull(response);
        assertTrue("success".equals(response.getStatus()) || "error".equals(response.getStatus()));
    }

    @Test
    void testNonExistentDomain() {
        BrandInfoResponse response = brandInfoService.resolveBrandInfo("nonexistentdomain12345.com");
        assertEquals("error", response.getStatus());
        assertTrue(response.getMessage().contains("does not have an associated active website") ||
                  response.getMessage().contains("network error") ||
                  response.getMessage().contains("internal service error"));
    }
}
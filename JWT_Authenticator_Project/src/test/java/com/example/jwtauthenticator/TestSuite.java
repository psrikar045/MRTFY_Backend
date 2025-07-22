package com.example.jwtauthenticator;

/**
 * Test suite configuration for the JWT Authenticator application
 * 
 * To run all tests, use:
 * mvn test
 * 
 * To run specific test categories:
 * mvn test -Dtest="*ServiceTest"
 * mvn test -Dtest="*ControllerTest"
 * mvn test -Dtest="*IntegrationTest"
 */
public class TestSuite {
    
    /**
     * This class serves as documentation for the test structure.
     * 
     * Available Test Categories:
     * 
     * Service Tests:
     * - AuthServiceTest
     * - UserServiceTest  
     * - EmailServiceTest
     * - TfaServiceTest
     * - PasswordResetServiceTest
     * - RateLimiterServiceTest
     * - GoogleTokenVerificationServiceTest
     * - BrandInfoServiceTest
     * - BrandCategoryResolutionServiceTest
     * - IdGeneratorServiceTest
     * 
     * Controller Tests:
     * - AuthControllerTest
     * - BrandInfoControllerTest
     * 
     * Integration Tests:
     * - AuthControllerIntegrationTest
     * - UserFlowIntegrationTest
     * 
     * Utility Tests:
     * - JwtUtilTest
     * - TestDataFactoryTest
     */
    
    // This is a documentation class - no executable code needed
}
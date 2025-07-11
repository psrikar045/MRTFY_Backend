package com.example.jwtauthenticator.model;

/**
 * This class contains example request and response payloads for Swagger documentation.
 * These examples help API consumers understand the expected format of requests and responses.
 */
public class ApiRequestExamples {

    /**
     * Example of a login request with brandId
     */
    public static final String LOGIN_REQUEST_WITH_BRAND = """
    {
      "username": "john_doe",
      "password": "SecurePassword123!",
      "brandId": "brand1"
    }
    """;
    
    /**
     * Example of a username-based login request
     */
    public static final String USERNAME_LOGIN_REQUEST = """
    {
      "username": "john_doe",
      "password": "SecurePassword123!"
    }
    """;
    
    /**
     * Example of an email-based login request
     */
    public static final String EMAIL_LOGIN_REQUEST = """
    {
      "email": "john.doe@example.com",
      "password": "SecurePassword123!"
    }
    """;
    
    /**
     * Example of a login response with token
     */
    public static final String LOGIN_RESPONSE = """
    {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "brandId": "brand1",
      "expirationTime": 36000
    }
    """;
    
    /**
     * Example of an error response when X-Brand-Id is missing
     */
    public static final String BRAND_ID_MISSING_ERROR = """
    {
      "error": "X-Brand-Id header is missing",
      "message": "Please include X-Brand-Id header in your request"
    }
    """;
    
    /**
     * Example of a protected endpoint request with proper headers
     */
    public static final String PROTECTED_REQUEST_HEADERS = """
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    X-Brand-Id: brand1
    """;
}
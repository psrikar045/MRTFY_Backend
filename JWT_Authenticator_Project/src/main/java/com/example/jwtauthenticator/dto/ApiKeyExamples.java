package com.example.jwtauthenticator.dto;

/**
 * Contains example JSON payloads for API documentation
 */
public class ApiKeyExamples {

    public static final String BASIC_API_KEY_CREATE_REQUEST = """
        {
            "name": "Free Tier API Key",
            "description": "Free tier API key for testing standard functionality",
            "registeredDomain": "example.com",
            "prefix": "sk-",
            "rateLimitTier": "FREE_TIER",
            "scopes": ["READ_USERS", "READ_BRANDS", "READ_CATEGORIES"]
        }
        """;

    public static final String ENHANCED_API_KEY_CREATE_REQUEST = """
        {
            "name": "Business Tier API Key",
            "description": "Business tier API key with unlimited access",
            "registeredDomain": "localhost.com",
            "allowedDomains": ["localhost", "127.0.0.1", "postman-echo.com"],
            "allowedIps": ["127.0.0.1", "::1", "192.168.1.0/24"],
            "rateLimitTier": "BUSINESS_TIER",
            "scopes": ["FULL_ACCESS", "DOMAINLESS_ACCESS", "ADMIN_ACCESS"],
            "prefix": "mrtfy-"
        }
        """;

    public static final String BUSINESS_API_KEY_CREATE_REQUEST = """
        {
            "name": "Pro Tier API Key",
            "description": "Pro tier API key for business operations",
            "registeredDomain": "business.example.com",
            "allowedDomains": ["api.business.com", "staging.business.com"],
            "allowedIps": ["192.168.1.100", "10.0.0.50"],
            "rateLimitTier": "PRO_TIER",
            "scopes": ["BUSINESS_READ", "BUSINESS_WRITE", "READ_USERS", "WRITE_USERS"],
            "expiresAt": "2024-12-31T23:59:59",
            "prefix": "biz-"
        }
        """;

    public static final String SERVER_API_KEY_CREATE_REQUEST = """
        {
            "name": "Backend Service API Key",
            "description": "Server-to-server communication API key",
            "registeredDomain": "internal.service.com",
            "rateLimitTier": "BUSINESS_TIER",
            "scopes": ["SERVER_ACCESS", "BACKEND_API", "INTERNAL_API", "SYSTEM_MONITOR"],
            "prefix": "srv-"
        }
        """;

    public static final String API_KEY_CREATE_RESPONSE = """
        {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "name": "Basic API Key",
            "keyValue": "sk-1234567890abcdef1234567890abcdef12345678"
        }
        """;

    public static final String API_KEY_LIST_RESPONSE = """
        [
            {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "name": "Basic API Key",
                "description": "Basic API key for testing",
                "registeredDomain": "example.com",
                "allowedDomains": [],
                "allowedIps": [],
                "rateLimitTier": "FREE_TIER",
                "scopes": ["READ_USERS", "READ_BRANDS"],
                "prefix": "sk-",
                "isActive": true,
                "createdAt": "2024-01-15T10:30:00Z",
                "expiresAt": null,
                "lastUsedAt": "2024-01-20T14:45:00Z",
                "usageCount": 150
            }
        ]
        """;

    public static final String API_KEY_UPDATE_REQUEST = """
        {
            "name": "Updated API Key Name",
            "description": "Updated description with enhanced functionality",
            "rateLimitTier": "PRO_TIER",
            "scopes": ["READ_USERS", "READ_BRANDS", "READ_CATEGORIES", "WRITE_BRANDS"]
        }
        """;

    public static final String DOMAIN_UPDATE_REQUEST = """
        {
            "registeredDomain": "newdomain.example.com",
            "allowedDomains": ["api.newdomain.com", "staging.newdomain.com"]
        }
        """;

    public static final String USAGE_STATS_RESPONSE = """
        {
            "apiKeyId": "550e8400-e29b-41d4-a716-446655440000",
            "period": {
                "startDate": "2024-01-01",
                "endDate": "2024-01-31",
                "granularity": "daily"
            },
            "totalRequests": 2450,
            "successfulRequests": 2380,
            "failedRequests": 70,
            "successRate": 97.14,
            "rateLimitHits": 5,
            "averageResponseTime": 245,
            "dailyBreakdown": [
                {
                    "date": "2024-01-01",
                    "requests": 85,
                    "successful": 82,
                    "failed": 3,
                    "avgResponseTime": 230
                }
            ],
            "topEndpoints": [
                {
                    "endpoint": "/api/v1/brands",
                    "requests": 1200,
                    "percentage": 48.98
                }
            ],
            "statusCodeBreakdown": {
                "200": 2100,
                "400": 25,
                "401": 15,
                "403": 20,
                "429": 5,
                "500": 5
            }
        }
        """;

    public static final String REQUEST_LOGS_RESPONSE = """
        {
            "content": [
                {
                    "id": "log-123456",
                    "timestamp": "2024-01-20T14:45:30Z",
                    "method": "GET",
                    "endpoint": "/api/v1/brands",
                    "statusCode": 200,
                    "responseTime": 245,
                    "requestSize": 0,
                    "responseSize": 1024,
                    "ipAddress": "192.168.1.100",
                    "userAgent": "PostmanRuntime/7.32.3",
                    "domain": "api.business.com"
                }
            ],
            "pageable": {
                "page": 0,
                "size": 50,
                "totalElements": 2450,
                "totalPages": 49
            }
        }
        """;

    public static final String ERROR_RESPONSE = """
        {
            "error": "Domain validation failed",
            "errorCode": "INVALID_DOMAIN_FORMAT",
            "message": "The provided domain 'invalid-domain' does not match the required format",
            "details": {
                "field": "registeredDomain",
                "rejectedValue": "invalid-domain",
                "expectedFormat": "example.com or subdomain.example.com"
            },
            "timestamp": "2024-01-20T14:45:30Z",
            "path": "/api/v1/api-keys/rivo-create-api"
        }
        """;

    public static final String DOMAIN_CHECK_RESPONSE = """
        {
            "domain": "testdomain.com",
            "available": true,
            "message": "Domain is available for registration",
            "suggestions": [
                "api.testdomain.com",
                "app.testdomain.com",
                "staging.testdomain.com"
            ]
        }
        """;

    public static final String SCOPES_REFERENCE_RESPONSE = """
        {
            "scopes": [
                {
                    "name": "READ_USERS",
                    "permission": "user.read",
                    "description": "Read user information and profiles",
                    "category": "Read Permissions"
                },
                {
                    "name": "FULL_ACCESS",
                    "permission": "*",
                    "description": "Complete system access (super admin equivalent)",
                    "category": "Special Permissions"
                }
            ],
            "totalCount": 47,
            "categories": [
                "Read Permissions",
                "Write Permissions",
                "Administrative",
                "Business Operations",
                "Server-to-Server",
                "Special Permissions"
            ]
        }
        """;

    public static final String RATE_LIMIT_TIERS_RESPONSE = """
        {
            "tiers": [
                {
                    "name": "FREE_TIER",
                    "displayName": "Free",
                    "description": "Free tier - 100 requests per month",
                    "requestsPerMonth": 100,
                    "requestsPerHour": 0.14,
                    "monthlyPrice": 0.0,
                    "isUnlimited": false
                },
                {
                    "name": "PRO_TIER",
                    "displayName": "Pro",
                    "description": "Pro tier - 1,000 requests per month",
                    "requestsPerMonth": 1000,
                    "requestsPerHour": 1.39,
                    "monthlyPrice": 25.0,
                    "isUnlimited": false
                },
                {
                    "name": "BUSINESS_TIER",
                    "displayName": "Business",
                    "description": "Business tier - Unlimited requests",
                    "requestsPerMonth": -1,
                    "monthlyPrice": 0.0,
                    "isUnlimited": true
                }
            ],
            "totalCount": 3,
            "defaultTier": "FREE_TIER",
            "adminDefaultTier": "BUSINESS_TIER"
        }
        """;
}
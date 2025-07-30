# JWT Authenticator - Complete Postman Testing Guide 2024

## Overview
This guide provides comprehensive instructions for testing the JWT Authenticator API using the complete Postman collection. The collection includes all endpoints with proper authentication flows and environment variable management.

## Files Included
1. **JWT_Authenticator_Complete_Collection_2024.json** - Complete API collection
2. **JWT_Authenticator_Environment_2024.json** - Environment variables
3. **POSTMAN_TESTING_GUIDE_2024.md** - This testing guide

## Setup Instructions

### 1. Import Collection and Environment
1. Open Postman
2. Click "Import" button
3. Import both JSON files:
   - `JWT_Authenticator_Complete_Collection_2024.json`
   - `JWT_Authenticator_Environment_2024.json`
4. Select the "JWT Authenticator Environment 2024" environment

### 2. Environment Variables
The environment includes the following pre-configured variables:

| Variable | Default Value | Description |
|----------|---------------|-------------|
| `base_url` | `http://202.65.155.125:8080/myapp` | API base URL |
| `test_username` | `testuser123` | Test username |
| `test_email` | `testuser@example.com` | Test email |
| `test_password` | `TestPassword123!` | Test password |
| `access_token` | (auto-set) | JWT access token |
| `refresh_token` | (auto-set) | JWT refresh token |
| `user_id` | (auto-set) | User ID |
| `api_key` | (auto-set) | API key |
| `brand_id` | `default` | Brand identifier |

## Testing Workflow

### Phase 1: Basic Authentication Flow
Execute these requests in order:

1. **Register User** (`POST /auth/register`)
   - Creates a new user account
   - Auto-sets `user_id` if successful

2. **Generate Token** (`POST /auth/token`)
   - Generates JWT tokens
   - Auto-sets `access_token` and `refresh_token`

3. **Login** (`POST /auth/login`)
   - Alternative login method
   - Auto-sets tokens

### Phase 2: Authentication Variations
Test different login methods:

4. **Login with Username** (`POST /auth/login/username`)
5. **Login with Email** (`POST /auth/login/email`)
6. **Refresh Token** (`POST /auth/refresh`)

### Phase 3: User Management
Test user-related operations:

7. **Update Profile** (`PUT /auth/profile`)
8. **Check Email Exists** (`POST /auth/check-email`)
9. **Check Username Exists** (`POST /auth/check-username`)

### Phase 4: API Key Management
Test API key operations:

10. **Create API Key** (`POST /api/v1/api-keys`)
    - Auto-sets `api_key` and `api_key_id`
11. **Get My API Keys** (`GET /api/v1/api-keys`)
12. **Update API Key** (`PUT /api/v1/api-keys/{keyId}`)
13. **Revoke API Key** (`PATCH /api/v1/api-keys/{keyId}/revoke`)

### Phase 5: Two-Factor Authentication
Test 2FA functionality:

14. **Setup 2FA** (`POST /auth/tfa/setup`)
15. **Get QR Code** (`GET /auth/tfa/qr-code`)
16. **Get Current TOTP Code** (`GET /auth/tfa/current-code`)
17. **Verify 2FA Code** (`POST /auth/tfa/verify`)
18. **Enable 2FA** (`POST /auth/tfa/enable`)

### Phase 6: Brand and Category Management
Test brand and category operations:

19. **Get All Brands** (`GET /api/brands/all`)
20. **Get Brand Info** (`GET /auth/brand-info`)
21. **Get Category Hierarchy** (`GET /api/category/hierarchy`)

### Phase 7: External API Access
Test API key authentication:

22. **External API Call** (`GET /api/external/test`)
    - Uses `X-API-Key` header with generated API key

### Phase 8: Advanced Features
Test additional features:

23. **Public Forward Request** (`POST /auth/public-forward`)
24. **ID Generation** (`POST /api/id-generator/user-id/generate`)
25. **Health Check** (`GET /actuator/health`)

## Authentication Methods

### 1. JWT Bearer Token Authentication
```
Authorization: Bearer {{access_token}}
```
Used for most authenticated endpoints.

### 2. API Key Authentication
```
X-API-Key: {{api_key}}
```
Used for external API access endpoints.

### 3. No Authentication
Public endpoints like registration, login, health check.

## Common Headers

### Required Headers for JWT Authentication:
- `Authorization: Bearer {{access_token}}`
- `Content-Type: application/json` (for POST/PUT requests)

### Optional Headers:
- `X-Brand-Id: {{brand_id}}` (for multi-tenant operations)

## Error Handling

### Common HTTP Status Codes:
- `200` - Success
- `201` - Created
- `400` - Bad Request (validation errors)
- `401` - Unauthorized (invalid/missing token)
- `403` - Forbidden (insufficient permissions)
- `404` - Not Found
- `429` - Too Many Requests (rate limiting)
- `500` - Internal Server Error

### Authentication Errors:
- Missing Authorization header
- Invalid JWT token
- Expired JWT token
- Invalid API key

## Testing Scenarios

### Scenario 1: Complete User Journey
1. Register → Generate Token → Update Profile → Create API Key → Use API Key

### Scenario 2: 2FA Setup and Usage
1. Register → Login → Setup 2FA → Get QR Code → Enable 2FA → Verify Code

### Scenario 3: API Key Lifecycle
1. Create API Key → Use API Key → Update API Key → Get Analytics → Revoke API Key

### Scenario 4: Brand Management
1. Get All Brands → Get Brand Info → Create Brand → Update Brand

### Scenario 5: Error Testing
1. Test with invalid tokens
2. Test with expired tokens
3. Test with missing headers
4. Test rate limiting

## Advanced Testing

### Rate Limiting Testing
- Make multiple rapid requests to test rate limiting
- Check for `429` status codes and `Retry-After` headers

### Token Expiration Testing
- Wait for token expiration (or modify JWT secret)
- Test refresh token functionality

### Multi-tenant Testing
- Use different `X-Brand-Id` values
- Test brand-specific operations

## Troubleshooting

### Common Issues:

1. **401 Unauthorized**
   - Check if `access_token` is set in environment
   - Verify token hasn't expired
   - Ensure correct Authorization header format

2. **400 Bad Request**
   - Check request body format
   - Verify required fields are present
   - Check Content-Type header

3. **404 Not Found**
   - Verify base URL is correct
   - Check endpoint path
   - Ensure server is running

4. **Connection Errors**
   - Verify server is accessible at `http://202.65.155.125:8080/myapp`
   - Check network connectivity
   - Verify server is running on correct port

### Debug Tips:
1. Enable Postman Console to see detailed request/response logs
2. Check environment variables are properly set
3. Verify server logs for detailed error information
4. Use the test scripts to automatically set environment variables

## Security Considerations

### Best Practices:
1. Never share real credentials in collections
2. Use environment variables for sensitive data
3. Regularly rotate API keys
4. Test with different user roles and permissions
5. Validate input sanitization and SQL injection protection

### Testing Security:
1. Test with malformed JWT tokens
2. Test with expired tokens
3. Test cross-user access (ensure users can't access other users' data)
4. Test input validation with special characters and SQL injection attempts

## Collection Structure

The collection is organized into logical groups:

1. **Authentication & User Management** - Core auth operations
2. **Two-Factor Authentication** - 2FA setup and verification
3. **API Key Management** - CRUD operations for API keys
4. **API Key Analytics & Statistics** - Usage analytics
5. **API Key Add-ons** - Add-on management
6. **Admin API Key Management** - Admin-level operations
7. **User Management** - User profile operations
8. **Brand Management** - Brand CRUD operations
9. **Category Management** - Category operations
10. **External API Access** - API key authenticated endpoints
11. **ID Generator** - ID generation utilities
12. **Protected Resources** - JWT authenticated endpoints
13. **Test Endpoints** - Testing and debugging
14. **File Upload** - File upload operations
15. **Health Check** - System health monitoring

## Automation

### Pre-request Scripts
The collection includes pre-request scripts that:
- Automatically set authentication headers
- Validate environment variables
- Handle token refresh if needed

### Test Scripts
The collection includes test scripts that:
- Automatically extract and set tokens from responses
- Validate response structure
- Set environment variables for subsequent requests

## Support

For issues or questions:
1. Check server logs for detailed error information
2. Verify all environment variables are correctly set
3. Ensure the server is running and accessible
4. Check the API documentation for endpoint specifications

---

**Last Updated:** December 2024
**Version:** 2024.1
**Compatible with:** JWT Authenticator API v1.0+
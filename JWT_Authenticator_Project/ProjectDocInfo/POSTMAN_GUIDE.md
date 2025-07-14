# JWT Authenticator API - Postman Guide

This guide explains how to use the Postman collection to test the JWT Authenticator API.

## Collection Files

The project includes a comprehensive Postman collection and environment:

1. **JWT_Authenticator_Complete_Collection.json** - Complete collection with all API endpoints
2. **JWT_Authenticator_Complete_Environment.json** - Environment variables for the collection

## Importing the Collection

1. Open Postman
2. Click "Import" in the top left corner
3. Select the collection file (`JWT_Authenticator_Complete_Collection.json`)
4. Select the environment file (`JWT_Authenticator_Complete_Environment.json`)
5. Click "Import"

## Setting Up the Environment

1. In Postman, click the environment dropdown in the top right corner
2. Select "JWT Authenticator Complete Environment"
3. The environment includes default values for testing, but you can modify them as needed

## Testing the API

### Authentication

1. **Register User**
   - Use the "Register User" request to create a new user
   - The response will include a verification token

2. **Verify Email**
   - Use the "Verify Email" request with the verification token
   - This step is required before login

3. **Login**
   - You can use any of the login methods:
     - "Login with Username" (new endpoint)
     - "Login with Email" (new endpoint)
     - "Login User (Legacy)" (old endpoint)
   - The response will include access and refresh tokens
   - The tokens will be automatically saved to the environment variables

### New Login Endpoints

The collection includes the new login endpoints:

1. **Login with Username**
   - Endpoint: `/auth/login/username`
   - Request body:
     ```json
     {
       "username": "{{testUsername}}",
       "password": "{{testPassword}}"
     }
     ```
   - No brandId required

2. **Login with Email**
   - Endpoint: `/auth/login/email`
   - Request body:
     ```json
     {
       "email": "{{testEmail}}",
       "password": "{{testPassword}}"
     }
     ```
   - No brandId required

Both endpoints return:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "brandId": "brand1",
  "expirationTime": 36000
}
```

### Protected Resources

After logging in, you can test protected resources:

1. **Get Protected Resource**
   - Requires the access token and X-Brand-Id header
   - The token is automatically included in the request
   - The X-Brand-Id header is set to the brandId from the environment

2. **Get Protected Resource - Missing X-Brand-Id**
   - This request will fail with a 400 Bad Request error
   - It demonstrates the requirement for the X-Brand-Id header

### Forward API

The collection includes requests for testing the Forward API:

1. **Forward Request - With X-Brand-Id**
   - Forwards a request to an external API
   - Requires the access token and X-Brand-Id header

2. **Forward Request - Missing X-Brand-Id**
   - This request will fail with a 400 Bad Request error
   - It demonstrates the requirement for the X-Brand-Id header

3. **Forward Request - Rate Limiting Test**
   - Run this request multiple times to test rate limiting

4. **Forward Request - Timeout Test**
   - Tests timeout handling with a slow-responding URL

## Environment Variables

The environment includes variables for testing:

- `baseUrl`: The base URL of the API (default: http://localhost:8080)
- `accessToken`: The JWT access token (set automatically after login)
- `refreshToken`: The JWT refresh token (set automatically after login)
- `brandId`: The brand ID for multi-tenant support (default: brand1)
- `testUsername`: The test username (default: testuser)
- `testEmail`: The test email (default: testuser@example.com)
- `testPassword`: The test password (default: password123)
- And many more for testing different features

## Notes

- The collection includes test scripts that automatically save tokens to environment variables
- The X-Brand-Id header is required for most protected endpoints
- The new login endpoints don't require brandId in the request body
- The response from the new login endpoints includes brandId and expirationTime
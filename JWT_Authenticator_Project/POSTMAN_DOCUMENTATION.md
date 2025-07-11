# JWT Authenticator API - Postman Collections

This document explains how to use the Postman collections to test the JWT Authenticator API.

## Available Collections

1. **JWT_Authenticator_Postman_Collection.json** - Complete collection with all API endpoints
2. **JWT_Authenticator_Clean_Postman_Collection.json** - Simplified collection with core endpoints
3. **JWT_Authenticator_X_Brand_Id_Demo.json** - Focused collection demonstrating X-Brand-Id header requirements
4. **Forward_API_Postman_Collection.json** - Collection specifically for testing the Forward API with X-Brand-Id header

## Environment Files

1. **JWT_Authenticator_Environment.json** - Environment variables for the main collection
2. **JWT_Authenticator_Clean_Environment.json** - Environment variables for the clean collection
3. **JWT_Authenticator_X_Brand_Id_Environment.json** - Environment variables for the X-Brand-Id demo
4. **Forward_API_Environment.json** - Environment variables for the Forward API collection

## X-Brand-Id Header Requirement

All protected endpoints in the JWT Authenticator API require the `X-Brand-Id` header. This header is used for multi-tenant support and must be included in all authenticated requests.

### Authentication Flow

1. **Login/Generate Token**
   - You can include the `brandId` in the request body during authentication
   - Example: `{"username": "testuser", "password": "password123", "brandId": "brand1"}`

2. **Protected Endpoints**
   - All protected endpoints require:
     - JWT token in the `Authorization` header
     - Brand ID in the `X-Brand-Id` header
   - Example headers:
     ```
     Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     X-Brand-Id: brand1
     ```

### Error Responses

If you forget to include the `X-Brand-Id` header, you will receive a 400 Bad Request error with a detailed message:

```json
{
  "timestamp": "2023-09-15T12:00:00.000Z",
  "status": 400,
  "error": "X-Brand-Id header is missing",
  "message": "Please include X-Brand-Id header in your request. This header is required for multi-tenant support.",
  "path": "/api/protected",
  "requiredHeaders": {
    "Authorization": "Bearer {jwt_token}",
    "X-Brand-Id": "{brand_id}"
  }
}
```

## Using the X-Brand-Id Demo Collection

The `JWT_Authenticator_X_Brand_Id_Demo.json` collection is specifically designed to demonstrate the X-Brand-Id header requirement:

1. **Authentication**
   - Login with brandId in the request body

2. **Protected Endpoints - With X-Brand-Id**
   - Successful requests with the X-Brand-Id header

3. **Protected Endpoints - Missing X-Brand-Id**
   - Failed requests without the X-Brand-Id header

4. **Error Responses**
   - Examples of different error responses

## Using the Forward API Collection

The `Forward_API_Postman_Collection.json` collection is specifically designed for testing the Forward API with the X-Brand-Id header requirement:

1. **Authentication**
   - Login to get a valid JWT token

2. **Forward API - With X-Brand-Id**
   - Forward requests with the X-Brand-Id header

3. **Forward API - Missing X-Brand-Id**
   - Forward requests without the X-Brand-Id header (will fail)

4. **Rate Limiting and Timeout Tests**
   - Tests for rate limiting and timeout handling

5. **Error Responses**
   - Examples of different error responses (missing X-Brand-Id, invalid token, rate limit exceeded)

## Importing Collections

1. Open Postman
2. Click "Import" button
3. Select the collection and environment files
4. Select the imported environment from the environment dropdown

## Running the Collections

1. Make sure the JWT Authenticator API is running
2. Set the `baseUrl` environment variable to match your API server
3. Run the "Login User" request first to get a valid JWT token
4. The token will be automatically saved to the environment variables
5. Run other requests as needed
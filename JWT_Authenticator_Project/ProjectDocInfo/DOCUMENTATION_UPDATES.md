# API Documentation Updates

## Changes Made

1. **Updated JwtRequestFilter**
   - Improved error response for missing X-Brand-Id header
   - Now returns a proper JSON error message instead of plain text

2. **Enhanced Swagger Configuration**
   - Added X-Brand-Id as a required security scheme
   - Improved documentation for JWT authentication
   - Added examples for request/response payloads

3. **Updated Controllers with Swagger Annotations**
   - Added detailed operation descriptions
   - Documented response codes and their meanings
   - Added parameter descriptions and examples
   - Included security requirements

4. **Added Example Request/Response Models**
   - Created ApiRequestExamples class with sample payloads
   - Documented proper format for authentication requests
   - Added examples of error responses

5. **Created Comprehensive README**
   - Added authentication flow documentation
   - Included examples of required headers
   - Provided sample requests and responses

## Key API Requirements

### Headers Required for Protected Endpoints

All protected endpoints require these headers:

```
Authorization: Bearer {jwt_token}
X-Brand-Id: {brand_id}
```

### Authentication Payload

When authenticating, include the brandId in the request body:

```json
{
  "username": "your_username",
  "password": "your_password",
  "brandId": "your_brand_id"
}
```

## Testing the API

1. First authenticate using `/auth/login` or `/auth/token` endpoints
2. Use the returned JWT token in the Authorization header
3. Include the X-Brand-Id header in all subsequent requests
4. Access protected resources like `/api/protected`

## Swagger UI

The full API documentation is available at:
```
http://localhost:8080/swagger-ui.html
```
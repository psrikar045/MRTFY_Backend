# JWT Authenticator API

A comprehensive JWT-based authentication system with multi-tenant support, email verification, two-factor authentication, and audit logging.

## Authentication Flow

### 1. Register a User

```http
POST /auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john.doe@example.com",
  "password": "SecurePassword123!",
  "brandId": "brand1"
}
```

### 2. Login to Get JWT Token

```http
POST /auth/login
Content-Type: application/json

{
  "username": "john_doe",
  "password": "SecurePassword123!",
  "brandId": "brand1"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "username": "john_doe",
  "roles": ["USER"]
}
```

### 3. Access Protected Resources

For all authenticated requests, you must include:
1. The JWT token in the `Authorization` header
2. The brand ID in the `X-Brand-Id` header

```http
GET /api/protected
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
X-Brand-Id: brand1
```

## Important Notes

### X-Brand-Id Header Requirement

All authenticated requests require the `X-Brand-Id` header. If this header is missing, you will receive a 400 Bad Request error:

```json
{
  "error": "X-Brand-Id header is missing",
  "message": "Please include X-Brand-Id header in your request"
}
```

### Authentication Payload

When authenticating, you can include the `brandId` in the request body:

```json
{
  "username": "john_doe",
  "password": "SecurePassword123!",
  "brandId": "brand1"
}
```

## API Documentation

The full API documentation is available at the Swagger UI endpoint:

```
/swagger-ui.html
```

## Features

- JWT-based authentication
- Multi-tenant support with brand IDs
- Email verification
- Two-factor authentication
- Password reset functionality
- Google Sign-In integration
- Rate limiting
- Request forwarding
- ID generation
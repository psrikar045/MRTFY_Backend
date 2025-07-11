# Login API Documentation

This document describes the new login APIs that support both username-based and email-based authentication.

## API Endpoints

### 1. Username-based Login

```http
POST /auth/login/username
Content-Type: application/json

{
  "username": "john_doe",
  "password": "SecurePassword123!"
}
```

### 2. Email-based Login

```http
POST /auth/login/email
Content-Type: application/json

{
  "email": "john.doe@example.com",
  "password": "SecurePassword123!"
}
```

## Response Format

Both APIs return the same response format:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "brandId": "brand1",
  "expirationTime": 36000
}
```

Where:
- `token`: JWT access token for authentication
- `refreshToken`: JWT refresh token for obtaining a new access token
- `brandId`: The brand ID associated with the user
- `expirationTime`: The expiration time of the access token in seconds

## Input Validation

### Username Validation
- Must be 3-20 characters long
- Can only contain letters, numbers, underscores, and hyphens

### Email Validation
- Must be a valid email format (e.g., contains "@" and a valid domain)

### Password Validation
- Must be at least 8 characters long

## Error Responses

### Invalid Credentials

```json
{
  "error": "Invalid username or password",
  "status": 400
}
```

### Email Not Verified

```json
{
  "error": "Email not verified. Please verify your email to login.",
  "status": 400
}
```

## Notes

- The brandId is no longer required in the login request
- The brandId is now returned in the response
- The expirationTime is now returned in the response
- The legacy `/auth/login` endpoint is still available but deprecated
# JWT Authenticator API Usage Guide

## Project Overview
This JWT Authenticator project provides a complete authentication and authorization solution with JWT tokens, user management, and security features.

## Getting Started

### Prerequisites
- Java 11 or higher
- PostgreSQL database
- Postman for API testing

### Running the Application
1. Ensure PostgreSQL is running
2. Start the application using:
   ```
   mvn spring-boot:run
   ```
3. The application will start on port 8080

## API Execution Flow

### 1. User Registration
- **Endpoint**: POST `/auth/register`
- **Description**: Register a new user
- **Request Body**:
  ```json
  {
    "username": "testuser",
    "password": "password123",
    "email": "testuser@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phoneNumber": "+1234567890",
    "location": "New York, USA",
    "brandId": "tenant1"
  }
  ```
- **Response**: User registration confirmation

### 2. Email Verification
- **Endpoint**: GET `/auth/verify-email?token={verification_token}`
- **Description**: Verify user email using token sent to email
- **Response**: Email verification confirmation

### 3. User Login
- **Endpoint**: POST `/auth/login`
- **Description**: Authenticate user and get JWT tokens
- **Request Body**:
  ```json
  {
    "username": "testuser",
    "password": "password123",
    "tenantId": "tenant1"
  }
  ```
- **Response**: JWT access and refresh tokens

### 4. Using Protected Endpoints
- Add the JWT token to the Authorization header:
  ```
  Authorization: Bearer {access_token}
  ```
- Add the tenant ID header:
  ```
  X-Brand-Id: tenant1
  ```

### 5. Token Refresh
- **Endpoint**: POST `/auth/refresh`
- **Description**: Get new tokens using refresh token
- **Request Body**: The refresh token as raw text
- **Response**: New access and refresh tokens

### 6. Password Management
- **Forgot Password**: POST `/auth/forgot-password`
  ```json
  {
    "email": "testuser@example.com"
  }
  ```
- **Reset Password**: POST `/auth/reset-password`
  ```json
  {
    "token": "reset-token-from-email",
    "password": "new-password"
  }
  ```

### 7. Two-Factor Authentication
- **Setup 2FA**: POST `/auth/tfa/setup?username={username}`
- **Verify 2FA Code**: POST `/auth/tfa/verify`
  ```json
  {
    "username": "testuser",
    "code": "123456"
  }
  ```
- **Enable/Disable 2FA**: 
  - POST `/auth/tfa/enable?username={username}`
  - POST `/auth/tfa/disable?username={username}`

### 8. Google Sign-In
- **Endpoint**: POST `/auth/google`
- **Description**: Authenticate using Google ID token
- **Request Body**:
  ```json
  {
    "idToken": "google-id-token"
  }
  ```
- **Response**: JWT access and refresh tokens

## Postman Collection Usage

1. Import the Postman collection: `JWT_Authenticator_Postman_Collection.json`
2. Import the environment: `JWT_Authenticator_Environment.json`
3. Set the environment variables:
   - `baseUrl`: `http://localhost:8080`
   - `testUsername`: Your test username
   - `testPassword`: Your test password
   - `testEmail`: Your test email
   - `tenantId`: `tenant1` (or your preferred tenant ID)

4. Execute requests in this order:
   - Register User
   - Login User (automatically saves tokens to environment)
   - Access protected endpoints
   - Refresh Token when needed

## Database Schema
The application uses JPA/Hibernate with automatic schema generation. The main entities are:

- **User**: Stores user information and credentials
- **PasswordResetToken**: Manages password reset requests
- **LoginLog**: Tracks login attempts and successes

## Security Features
- JWT-based authentication
- Password encryption with BCrypt
- Multi-tenant architecture
- Two-factor authentication
- Email verification
- Refresh token rotation
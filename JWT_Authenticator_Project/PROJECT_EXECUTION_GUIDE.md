# JWT Authenticator Project - Execution Guide

## Project Overview
This JWT Authenticator provides a complete authentication and authorization solution with JWT tokens, user management, and security features.

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

## Project Structure

### Key Components
- **Controllers**: Handle HTTP requests
- **Services**: Implement business logic
- **Repositories**: Manage database operations
- **Models/Entities**: Define data structures
- **Security**: JWT authentication and authorization

### Main Features
1. **JWT Authentication**: Secure token-based authentication
2. **User Management**: Registration, login, profile management
3. **Password Management**: Reset, forgot password functionality
4. **Two-Factor Authentication**: Enhanced security with TOTP
5. **Google Sign-In**: OAuth2 integration
6. **Multi-tenant Architecture**: Support for multiple tenants/brands

## Database Setup
The application uses JPA/Hibernate with automatic schema generation. When you run the application, it will:
1. Connect to the configured PostgreSQL database
2. Create necessary tables based on entity classes
3. Apply any schema updates automatically

## Testing with Postman
1. Import the Postman collection: `JWT_Authenticator_Clean_Postman_Collection.json`
2. Import the environment: `JWT_Authenticator_Clean_Environment.json`
3. Set the environment variables as needed
4. Execute requests in the following order:
   - Register User
   - Login User
   - Access protected endpoints
   - Test other features as needed

## Google OAuth Setup
To use Google Sign-In:
1. Ensure the `google.oauth2.client-id` property is set in application.properties
2. Use the Google Sign-In endpoint in the Postman collection
3. Provide a valid Google ID token

## Security Considerations
- JWT tokens are signed with a secret key defined in application.properties
- Passwords are encrypted using BCrypt
- Multi-tenant architecture ensures data isolation
- Two-factor authentication adds an extra layer of security

## Endpoint Authentication Requirements

| Endpoint | Authentication | Type |
| --- | --- | --- |
| `POST /auth/register` | Not required | - |
| `POST /auth/token` | Not required | - |
| `POST /auth/login` | Not required | - |
| `POST /auth/login/username` | Not required | - |
| `POST /auth/login/email` | Not required | - |
| `POST /auth/refresh` | Not required (requires refresh token body) | - |
| `PUT /auth/profile` | Required | Bearer token & `X-Brand-Id` |
| `POST /auth/check-email` | Not required | - |
| `POST /auth/forgot-password` | Not required | - |
| `POST /auth/forgot-password-code` | Not required | - |
| `POST /auth/verify-reset-code` | Not required | - |
| `POST /auth/set-new-password` | Not required | - |
| `GET /api/protected` | Required | Bearer token & `X-Brand-Id` |
| `POST /forward` | Required | Bearer token & `X-Brand-Id` |
| `POST /api/id-generator/generate` | Not required | - |
| `POST /api/id-generator/generate/{prefix}` | Not required | - |

## API Documentation
- Swagger UI: http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/v3/api-docs
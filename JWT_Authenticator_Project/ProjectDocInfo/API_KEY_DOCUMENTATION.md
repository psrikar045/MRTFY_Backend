# API Key Management Documentation

## Overview

The API Key functionality provides a comprehensive system for managing API keys with advanced security features including rate limiting, IP restrictions, domain restrictions, and scope-based permissions.

## Features

### Core Features
- **Secure Key Generation**: 256-bit entropy with cryptographically secure random generation
- **SHA-256 Hashing**: Keys are stored as irreversible hashes
- **Rate Limiting**: Configurable rate limits per API key
- **IP Restrictions**: Whitelist specific IP addresses
- **Domain Restrictions**: Whitelist specific domains
- **Scope-based Permissions**: Fine-grained access control
- **Expiration Management**: Set expiration dates for keys
- **Usage Tracking**: Track last used timestamps
- **Audit Logging**: Comprehensive logging of all operations

### Security Features
- **Constant-time Comparison**: Prevents timing attacks
- **Format Validation**: Validates API key format before processing
- **Prefix Support**: Different prefixes for different key types (sk-, admin-, biz-)
- **Automatic Cleanup**: Scheduled cleanup of expired keys
- **Rate Limit Headers**: Provides rate limit information in response headers

## API Endpoints

### User API Key Management (`/api/v1/api-keys`)

#### Create API Key
```http
POST /api/v1/api-keys
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "My API Key",
  "description": "API key for my application",
  "prefix": "sk-",
  "expiresAt": "2024-12-31T23:59:59",
  "allowedIps": ["192.168.1.100", "10.0.0.1"],
  "allowedDomains": ["example.com", "api.example.com"],
  "rateLimitTier": "STANDARD",
  "scopes": ["READ_USERS", "READ_BRANDS"]
}
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "My API Key",
  "keyValue": "sk-AbCdEf123456789..."
}
```

#### List API Keys
```http
GET /api/v1/api-keys
Authorization: Bearer <jwt-token>
```

#### Get Specific API Key
```http
GET /api/v1/api-keys/{keyId}
Authorization: Bearer <jwt-token>
```

#### Update API Key
```http
PUT /api/v1/api-keys/{keyId}
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "Updated API Key Name",
  "description": "Updated description",
  "isActive": true,
  "expiresAt": "2025-12-31T23:59:59"
}
```

#### Revoke API Key
```http
PATCH /api/v1/api-keys/{keyId}/revoke
Authorization: Bearer <jwt-token>
```

#### Delete API Key
```http
DELETE /api/v1/api-keys/{keyId}
Authorization: Bearer <jwt-token>
```

### Admin API Key Management (`/api/admin/api-keys`)

#### Get All API Keys (Admin)
```http
GET /api/admin/api-keys/all
Authorization: Bearer <admin-jwt-token>
```

#### Get API Keys for User (Admin)
```http
GET /api/admin/api-keys/user/{userId}
Authorization: Bearer <admin-jwt-token>
```

#### Create API Key for User (Admin)
```http
POST /api/admin/api-keys/user/{userId}
Authorization: Bearer <admin-jwt-token>
Content-Type: application/json

{
  "name": "Admin Created Key",
  "description": "Key created by admin",
  "rateLimitTier": "PREMIUM",
  "scopes": ["FULL_ACCESS"]
}
```

#### Get System Statistics (Admin)
```http
GET /api/admin/api-keys/stats
Authorization: Bearer <admin-jwt-token>
```

#### Reset Rate Limit (Admin)
```http
POST /api/admin/api-keys/{keyId}/rate-limit/reset
Authorization: Bearer <admin-jwt-token>
```

#### Update API Key Scopes (Admin)
```http
PUT /api/admin/api-keys/{keyId}/scopes
Authorization: Bearer <admin-jwt-token>
Content-Type: application/json

{
  "scopes": "READ_USERS,WRITE_BRANDS,ADMIN_ACCESS"
}
```

## Using API Keys

### Authentication Header
```http
X-API-KEY: sk-your-api-key-here
```

### Example Request
```http
GET /api/v1/brands/all
X-API-KEY: sk-AbCdEf123456789...
```

### Rate Limit Headers
The API returns rate limit information in response headers:
- `X-RateLimit-Limit`: Maximum requests allowed in the time window
- `X-RateLimit-Remaining`: Remaining requests in the current window
- `X-RateLimit-Reset`: Unix timestamp when the rate limit resets

## Rate Limit Tiers

| Tier | Requests per day | Description |
|------|------------------|-------------|
| BASIC | 100 | Basic tier for development |
| STANDARD | 1,000 | Standard tier for small applications |
| PREMIUM | 10,000 | Premium tier for production applications |
| ENTERPRISE | 50,000 | Enterprise tier for high-volume applications |
| UNLIMITED | ∞ | No rate limits (admin only) |

## API Key Scopes

### Read Permissions
- `READ_USERS`: Read user information and profiles
- `READ_BRANDS`: Read brand information and assets
- `READ_CATEGORIES`: Read category hierarchy and information
- `READ_API_KEYS`: Read own API key information

### Write Permissions
- `WRITE_USERS`: Create and update user information
- `WRITE_BRANDS`: Create and update brand information
- `WRITE_CATEGORIES`: Create and update categories

### Delete Permissions
- `DELETE_USERS`: Delete user accounts
- `DELETE_BRANDS`: Delete brands and assets
- `DELETE_CATEGORIES`: Delete categories

### API Key Management
- `MANAGE_API_KEYS`: Full API key management capabilities
- `REVOKE_API_KEYS`: Revoke and deactivate API keys

### Admin Permissions
- `ADMIN_ACCESS`: Access administrative functions
- `SYSTEM_MONITOR`: Monitor system health and metrics

### Special Permissions
- `FULL_ACCESS`: Complete system access (super admin equivalent)

### Business API Permissions
- `BUSINESS_READ`: Read business-related data via API
- `BUSINESS_WRITE`: Write business-related data via API

## Security Best Practices

### For API Key Creators
1. **Use Descriptive Names**: Give your API keys meaningful names
2. **Set Expiration Dates**: Don't create keys that never expire
3. **Limit Scopes**: Only grant the minimum required permissions
4. **Restrict IPs/Domains**: Limit access to specific IPs or domains when possible
5. **Monitor Usage**: Regularly check your API key usage
6. **Rotate Keys**: Periodically create new keys and revoke old ones

### For API Key Users
1. **Store Securely**: Never commit API keys to version control
2. **Use Environment Variables**: Store keys in environment variables
3. **Handle Rate Limits**: Implement proper rate limit handling
4. **Monitor Headers**: Check rate limit headers in responses
5. **Handle Errors**: Properly handle authentication and authorization errors

### For Administrators
1. **Regular Audits**: Regularly audit API key usage
2. **Monitor Statistics**: Keep track of system-wide API key statistics
3. **Clean Up**: Remove unused or expired keys
4. **Set Appropriate Limits**: Configure appropriate rate limits for different tiers

## Error Handling

### Common Error Responses

#### Invalid API Key
```json
{
  "error": "Invalid API key",
  "timestamp": "2024-01-15T10:30:00",
  "status": 401
}
```

#### Rate Limit Exceeded
```json
{
  "error": "Rate limit exceeded",
  "timestamp": "2024-01-15T10:30:00",
  "status": 429
}
```

#### IP Address Not Allowed
```json
{
  "error": "IP address not allowed",
  "timestamp": "2024-01-15T10:30:00",
  "status": 403
}
```

#### Insufficient Scope
```json
{
  "error": "Insufficient scope for this operation",
  "timestamp": "2024-01-15T10:30:00",
  "status": 403
}
```

## Database Schema

### api_keys Table
```sql
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_fk_id VARCHAR(11) NOT NULL REFERENCES users(id),
    key_hash VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    prefix VARCHAR(10),
    is_active BOOLEAN NOT NULL DEFAULT true,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    revoked_at TIMESTAMP,
    allowed_ips TEXT,
    allowed_domains TEXT,
    rate_limit_tier VARCHAR(50),
    scopes TEXT
);
```

## Configuration

### Application Properties
```properties
# API Key Configuration
app.api-key.max-keys-per-user=10
app.api-key.default-rate-limit-tier=BASIC
app.api-key.cleanup.enabled=true
app.api-key.cleanup.cron=0 0 2 * * ?
```

## Monitoring and Maintenance

### Scheduled Tasks
- **Daily Cleanup**: Automatically deactivates expired keys at 2:00 AM
- **Weekly Statistics**: Logs usage statistics every Sunday at 1:00 AM

### Manual Maintenance
Administrators can perform manual cleanup and get statistics through the admin API endpoints.

### Logging
All API key operations are logged with appropriate log levels:
- **INFO**: Successful operations
- **WARN**: Security-related events (invalid keys, rate limits, IP restrictions)
- **ERROR**: System errors and failures

## Testing

### Unit Tests
- `ApiKeyServiceTest`: Tests for service layer functionality
- `ApiKeyControllerTest`: Tests for controller layer functionality
- `ApiKeyHashUtilTest`: Tests for cryptographic utilities

### Integration Tests
- End-to-end API key creation and usage
- Rate limiting functionality
- Security restrictions (IP, domain, scope)

## Troubleshooting

### Common Issues

1. **API Key Not Working**
   - Check if key is active and not expired
   - Verify correct header format (`X-API-KEY`)
   - Check IP/domain restrictions

2. **Rate Limit Issues**
   - Check rate limit headers in response
   - Verify rate limit tier configuration
   - Consider upgrading to higher tier

3. **Scope Issues**
   - Verify required scopes are granted to the API key
   - Check scope configuration in API key settings

4. **Performance Issues**
   - Monitor API key usage statistics
   - Consider implementing caching for frequently used keys
   - Review rate limit configurations
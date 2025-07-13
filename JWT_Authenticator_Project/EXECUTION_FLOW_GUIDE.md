# JWT Authenticator Project - Execution Flow Guide

## Overview
This document provides a comprehensive guide for executing and testing the JWT Authenticator Project, including the new public forward endpoint functionality and the Brand Data Extraction System.

## Table of Contents
1. [Project Setup](#project-setup)
2. [Application Startup](#application-startup)
3. [API Testing Flow](#api-testing-flow)
4. [New Features Testing](#new-features-testing)
5. [Brand Data Extraction Testing](#brand-data-extraction-testing)
6. [Troubleshooting](#troubleshooting)
7. [Performance Testing](#performance-testing)

## Project Setup

### Prerequisites
- **Java**: Version 21 or higher
- **Maven**: Version 3.6 or higher
- **PostgreSQL**: Version 12 or higher (for production)
- **Postman**: For API testing
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code

### Environment Configuration

#### 1. Database Setup (PostgreSQL)
```sql
-- Create database
CREATE DATABASE jwt_authenticator;

-- Create user (optional)
CREATE USER jwt_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE jwt_authenticator TO jwt_user;
```

#### 2. Application Properties
Update `src/main/resources/application-postgres.properties`:
```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/jwt_authenticator
spring.datasource.username=jwt_user
spring.datasource.password=your_password

# Server Configuration
server.port=8080
server.servlet.context-path=/myapp

# Application URLs
app.base-url=http://localhost:8080/myapp
app.frontend-url=http://localhost:4200
```

## Application Startup

### 1. Build the Project
```bash
cd JWT_Authenticator_Project
mvn clean compile
```

### 2. Run Tests
```bash
mvn test
```

### 3. Start the Application
```bash
mvn spring-boot:run
```

### 4. Verify Startup
- Application should start on port 8080
- Check logs for successful database connection
- Access Swagger UI: `http://localhost:8080/myapp/swagger-ui.html`

## API Testing Flow

### Phase 1: Basic Authentication Flow

#### 1. User Registration
```http
POST /myapp/auth/register
Content-Type: application/json

{
    "username": "testuser123",
    "password": "TestPassword123!",
    "email": "test@example.com",
    "firstName": "Test",
    "lastName": "User",
    "phoneNumber": "+1234567890",
    "location": "Test City",
    "brandId": "MRTFY000001"
}
```

**Expected Response:**
```json
{
    "success": true,
    "message": "User registered successfully",
    "userId": "DOMBR000001"
}
```

#### 2. User Login (Username)
```http
POST /myapp/auth/login/username
Content-Type: application/json

{
    "username": "testuser123",
    "password": "TestPassword123!"
}
```

**Expected Response:**
```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "userId": "DOMBR000001",
    "brandId": "MRTFY000001"
}
```

#### 3. User Login (Email)
```http
POST /myapp/auth/login/email
Content-Type: application/json

{
    "email": "test@example.com",
    "password": "TestPassword123!"
}
```

### Phase 2: Protected Resource Access

#### 1. Access Protected Endpoint
```http
GET /myapp/api/protected
Authorization: Bearer {accessToken}
```

**Expected Response:**
```json
{
    "message": "Hello, this is a protected resource!",
    "userId": "DOMBR000001",
    "timestamp": "2025-07-12T08:30:00Z"
}
```

#### 2. Get User Profile
```http
GET /myapp/api/user-profile
Authorization: Bearer {accessToken}
```

### Phase 3: Forward Functionality Testing

#### 1. Authenticated Forward Request (Updated - No X-Brand-Id)
```http
POST /myapp/api/forward
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "url": "https://httpbin.org/json"
}
```

**Expected Response:**
```json
{
    "slideshow": {
        "author": "Yours Truly",
        "date": "date of publication",
        "slides": [...]
    }
}
```

#### 2. Public Forward Request (NEW FEATURE)
```http
POST /myapp/auth/public-forward
Content-Type: application/json

{
    "url": "https://httpbin.org/json"
}
```

**Expected Response:**
```json
{
    "slideshow": {
        "author": "Yours Truly",
        "date": "date of publication",
        "slides": [...]
    }
}
```

**Key Differences:**
- ✅ No authentication required
- ✅ No X-Brand-Id header needed
- ✅ IP-based rate limiting (more restrictive)
- ✅ Same core forwarding functionality

## New Features Testing

### 1. Public Forward Endpoint Validation

#### Test 1: Basic Functionality
```bash
curl -X POST http://localhost:8080/myapp/auth/public-forward \
  -H "Content-Type: application/json" \
  -d '{"url": "https://httpbin.org/json"}'
```

#### Test 2: Invalid URL Handling
```bash
curl -X POST http://localhost:8080/myapp/auth/public-forward \
  -H "Content-Type: application/json" \
  -d '{"url": "invalid-url"}'
```

**Expected Response:**
```json
{
    "error": "Invalid URL",
    "status": 400,
    "timestamp": "2025-07-12T08:30:00Z"
}
```

#### Test 3: Rate Limiting
Execute multiple requests rapidly:
```bash
for i in {1..60}; do
  curl -X POST http://localhost:8080/myapp/auth/public-forward \
    -H "Content-Type: application/json" \
    -d '{"url": "https://httpbin.org/json"}' &
done
```

**Expected Behavior:**
- First ~50 requests: HTTP 200
- Subsequent requests: HTTP 429 with Retry-After header

## Brand Data Extraction Testing

### 1. Automatic Brand Extraction via Forward Service

#### Test 1: Forward Request with Brand Extraction
```bash
curl -X POST http://localhost:8080/myapp/api/forward \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://versa-networks.com"}'
```

**Expected Behavior:**
- ✅ Returns original API response
- ✅ Triggers brand data extraction in background
- ✅ Stores brand data in database
- ✅ Downloads brand assets asynchronously

#### Test 2: Public Forward with Brand Extraction
```bash
curl -X POST http://localhost:8080/myapp/auth/public-forward \
  -H "Content-Type: application/json" \
  -d '{"url": "https://versa-networks.com"}'
```

**Expected Behavior:**
- ✅ Works without authentication
- ✅ Triggers brand data extraction
- ✅ Same extraction functionality as authenticated endpoint

### 2. Manual Brand Extraction Testing

#### Test 1: Manual Extraction with Sample Data
```bash
curl -X POST "http://localhost:8080/myapp/api/brands/extract?url=https://versa-networks.com" \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json"
```

**Expected Response:**
```json
{
    "success": true,
    "message": "Brand extraction completed successfully",
    "brandId": 1,
    "brandData": {
        "id": 1,
        "name": "Versa Networks",
        "website": "https://versa-networks.com/",
        "assets": [...],
        "colors": [...],
        "fonts": [...],
        "socialLinks": [...],
        "images": [...]
    }
}
```

#### Test 2: Manual Extraction with Custom Mock Data
```bash
curl -X POST "http://localhost:8080/myapp/api/brands/extract?url=https://example.com" \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "mockResponse": "{\"Company\":{\"Name\":\"Test Company\",\"Website\":\"https://example.com\"}}"
  }'
```

### 3. Brand Data Retrieval Testing

#### Test 1: Get Brand by Website
```bash
curl -X GET "http://localhost:8080/myapp/api/brands/by-website?website=https://versa-networks.com" \
  -H "Authorization: Bearer {accessToken}"
```

#### Test 2: Search Brands
```bash
curl -X GET "http://localhost:8080/myapp/api/brands/search?q=versa&page=0&size=10" \
  -H "Authorization: Bearer {accessToken}"
```

#### Test 3: Get All Brands (Paginated)
```bash
curl -X GET "http://localhost:8080/myapp/api/brands?page=0&size=20" \
  -H "Authorization: Bearer {accessToken}"
```

#### Test 4: Get Brand Statistics
```bash
curl -X GET "http://localhost:8080/myapp/api/brands/statistics" \
  -H "Authorization: Bearer {accessToken}"
```

**Expected Response:**
```json
{
    "totalBrands": 5,
    "brandsCreatedLastMonth": 3
}
```

### 4. Asset Serving Testing

#### Test 1: Access Brand Logo (Public)
```bash
curl -X GET "http://localhost:8080/myapp/api/brands/assets/1" \
  -o brand_logo.svg
```

**Expected Behavior:**
- ✅ Returns image file if downloaded
- ✅ Redirects to original URL if not downloaded
- ✅ No authentication required

#### Test 2: Access Brand Image (Public)
```bash
curl -X GET "http://localhost:8080/myapp/api/brands/images/1" \
  -o brand_image.webp
```

### 5. Database Verification

#### Check Brand Data in Database
```sql
-- Check extracted brands
SELECT id, name, website, created_at, last_extraction_timestamp 
FROM brands 
ORDER BY created_at DESC;

-- Check brand assets
SELECT ba.id, ba.asset_type, ba.original_url, ba.download_status, b.name
FROM brand_assets ba
JOIN brands b ON ba.brand_id = b.id
ORDER BY ba.created_at DESC;

-- Check brand colors
SELECT bc.hex_code, bc.color_name, bc.usage_context, b.name
FROM brand_colors bc
JOIN brands b ON bc.brand_id = b.id;

-- Check download statistics
SELECT 
    download_status,
    COUNT(*) as count
FROM brand_assets 
GROUP BY download_status;
```

### 6. File Storage Verification

#### Check Local File Storage
```bash
# Check if brand assets directory exists
ls -la ./brand-assets/

# Check brand-specific directories
ls -la ./brand-assets/brands/

# Check downloaded files
find ./brand-assets -name "*.svg" -o -name "*.png" -o -name "*.webp"
```

### 7. Error Handling Testing

#### Test 1: Invalid URL
```bash
curl -X POST "http://localhost:8080/myapp/api/brands/extract?url=invalid-url" \
  -H "Authorization: Bearer {accessToken}"
```

**Expected Response:**
```json
{
    "error": "Brand extraction failed: ...",
    "timestamp": "2025-07-12T10:00:00Z"
}
```

#### Test 2: Missing Authentication
```bash
curl -X GET "http://localhost:8080/myapp/api/brands/1"
```

**Expected Response:** HTTP 401 Unauthorized

#### Test 3: Non-existent Brand
```bash
curl -X GET "http://localhost:8080/myapp/api/brands/999999" \
  -H "Authorization: Bearer {accessToken}"
```

**Expected Response:**
```json
{
    "error": "Brand not found with ID: 999999",
    "timestamp": "2025-07-12T10:00:00Z"
}
```

### 2. X-Brand-Id Removal Validation

#### Test 1: Authenticated Forward Without X-Brand-Id
```bash
curl -X POST http://localhost:8080/myapp/api/forward \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://httpbin.org/json"}'
```

**Expected Result:** ✅ Should work without X-Brand-Id header

#### Test 2: Swagger Documentation Check
1. Visit: `http://localhost:8080/myapp/swagger-ui.html`
2. Navigate to `/api/forward` endpoint
3. Verify X-Brand-Id parameter is not listed in required headers

## Performance Testing

### 1. Rate Limiting Performance

#### Authenticated Endpoint
- **Limit**: 100 requests/minute per user
- **Test**: Send 120 requests in 1 minute
- **Expected**: First 100 succeed, remaining 20 get HTTP 429

#### Public Endpoint
- **Limit**: 50 requests/minute per IP
- **Test**: Send 60 requests in 1 minute
- **Expected**: First 50 succeed, remaining 10 get HTTP 429

### 2. Response Time Testing

#### Forward Request Performance
```bash
# Test response time
time curl -X POST http://localhost:8080/myapp/auth/public-forward \
  -H "Content-Type: application/json" \
  -d '{"url": "https://httpbin.org/json"}'
```

**Expected**: Response time < 5 seconds for most requests

### 3. Concurrent Request Testing

#### Load Testing Script
```bash
#!/bin/bash
# Test concurrent requests
for i in {1..10}; do
  (
    for j in {1..5}; do
      curl -X POST http://localhost:8080/myapp/auth/public-forward \
        -H "Content-Type: application/json" \
        -d '{"url": "https://httpbin.org/json"}' \
        -w "Response time: %{time_total}s\n"
    done
  ) &
done
wait
```

## Troubleshooting

### Common Issues

#### 1. Application Won't Start
**Symptoms:**
- Port already in use
- Database connection failed

**Solutions:**
```bash
# Check port usage
netstat -an | grep 8080

# Kill process using port
taskkill /F /PID <process_id>

# Check database connection
psql -h localhost -U jwt_user -d jwt_authenticator
```

#### 2. Authentication Failures
**Symptoms:**
- HTTP 401 responses
- Invalid token errors

**Solutions:**
```bash
# Verify token format
echo "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." | base64 -d

# Check token expiration
# Use JWT debugger: https://jwt.io/
```

#### 3. Forward Request Failures
**Symptoms:**
- HTTP 504 Gateway Timeout
- Connection refused errors

**Solutions:**
```bash
# Test external URL directly
curl -I https://httpbin.org/json

# Check network connectivity
ping httpbin.org

# Verify firewall settings
```

#### 4. Rate Limiting Issues
**Symptoms:**
- Unexpected HTTP 429 responses
- Rate limits not working

**Solutions:**
```bash
# Check application logs
tail -f logs/application.log

# Verify rate limit configuration
grep "rate-limit" src/main/resources/application.properties

# Clear rate limit buckets (restart application)
```

### Debug Mode

#### Enable Debug Logging
Add to `application.properties`:
```properties
logging.level.com.example.jwtauthenticator=DEBUG
logging.level.org.springframework.security=DEBUG
```

#### Monitor Application Metrics
```bash
# Check application health
curl http://localhost:8080/myapp/actuator/health

# View application info
curl http://localhost:8080/myapp/actuator/info
```

## Testing Checklist

### Pre-Deployment Checklist
- [ ] All tests pass (`mvn test`)
- [ ] Application starts successfully
- [ ] Database connection established
- [ ] Swagger UI accessible
- [ ] All authentication endpoints working
- [ ] Forward endpoints working (both authenticated and public)
- [ ] Rate limiting functioning correctly
- [ ] Error handling working properly

### Post-Deployment Checklist
- [ ] Public forward endpoint accessible without authentication
- [ ] Authenticated forward endpoint works without X-Brand-Id
- [ ] Rate limiting enforced correctly
- [ ] Performance within acceptable limits
- [ ] Logs showing proper request tracking
- [ ] Error responses properly formatted

## Monitoring and Maintenance

### Log Monitoring
```bash
# Monitor application logs
tail -f logs/application.log | grep -E "(ERROR|WARN|public-forward|forward)"

# Monitor rate limiting
tail -f logs/application.log | grep "Rate limit"

# Monitor performance
tail -f logs/application.log | grep "duration="
```

### Health Checks
```bash
# Application health
curl http://localhost:8080/myapp/actuator/health

# Database connectivity
curl http://localhost:8080/myapp/actuator/health/db

# Custom health indicators
curl http://localhost:8080/myapp/actuator/health/custom
```

---

## Conclusion

This execution flow guide provides comprehensive instructions for testing and validating the JWT Authenticator Project with the new public forward endpoint functionality. Follow the phases sequentially to ensure all features are working correctly.

For additional support, refer to:
- [Project Validation Report](PROJECT_VALIDATION_REPORT.md)
- [API Documentation](API_USAGE_GUIDE.md)
- [Postman Collection](JWT_Authenticator_Updated_Collection.json)

---
*Last Updated: 2025-07-12*
*Version: 3.0.0*
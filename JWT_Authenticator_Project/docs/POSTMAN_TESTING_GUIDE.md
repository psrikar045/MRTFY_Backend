# 🧪 Complete Postman Testing Guide

## 📋 **Updated Postman Collection Overview**

I have created a comprehensive updated Postman collection that includes **all the new features** we've implemented:

### **📁 Collection Files:**
- **Collection**: `JWT_Authenticator_Complete_Collection_Updated.json`
- **Environment**: `JWT_Authenticator_Environment_Updated.json`

---

## 🚀 **Testing Flow - Step by Step**

### **1. 👤 User Management**
Test user registration and authentication:

1. **Register User** - Creates a new test user
2. **User Login** - Gets JWT token for regular user operations
3. **Admin Login** - Gets JWT token for admin operations

**Expected Results:**
- ✅ JWT tokens saved to environment variables
- ✅ User IDs captured for later use

---

### **2. 🔑 API Key Management - Basic**
Test all API key creation scenarios:

#### **2.1 Create API Key - Default Prefix**
```json
{
  "name": "Basic API Key",
  "description": "API key with default prefix",
  "rateLimitTier": "BASIC",
  "scopes": ["READ", "WRITE"]
}
```
**Expected Result:** API key with `sk-` prefix

#### **2.2 Create API Key - Custom Prefix**
```json
{
  "name": "Production API Key",
  "prefix": "prod-",
  "rateLimitTier": "PREMIUM",
  "scopes": ["READ", "WRITE", "ADMIN"]
}
```
**Expected Result:** API key with `prod-` prefix

#### **2.3 Create API Key - With IP Restrictions**
```json
{
  "name": "IP Restricted API Key",
  "prefix": "secure-",
  "allowedIps": ["127.0.0.1", "192.168.1.100"],
  "rateLimitTier": "STANDARD",
  "scopes": ["READ"]
}
```
**Expected Result:** API key with IP restrictions

#### **2.4 Create API Key - With Domain Restrictions**
```json
{
  "name": "Domain Restricted API Key",
  "prefix": "domain-",
  "allowedDomains": ["localhost", "myapp.com", "*.myapp.com"],
  "rateLimitTier": "STANDARD",
  "scopes": ["READ", "WRITE"]
}
```
**Expected Result:** API key with domain restrictions

#### **2.5 Create API Key - Full Featured**
```json
{
  "name": "Enterprise API Key",
  "prefix": "enterprise-",
  "allowedIps": ["127.0.0.1", "192.168.1.0/24"],
  "allowedDomains": ["localhost", "api.mycompany.com", "*.mycompany.com"],
  "scopes": ["READ", "WRITE", "DELETE", "ADMIN", "ANALYTICS"],
  "rateLimitTier": "ENTERPRISE",
  "expiresAt": "2025-12-31T23:59:59"
}
```
**Expected Result:** API key with all features enabled

---

### **3. 🧪 API Key Usage Testing**
Test how API keys work in practice:

#### **3.1 Test API Key - Success (Default Prefix)**
- Uses basic API key with `sk-` prefix
- **Expected**: ✅ Request succeeds, logs created automatically

#### **3.2 Test API Key - Custom Prefix**
- Uses custom prefix API key (`prod-`)
- **Expected**: ✅ Request succeeds, custom prefix validated

#### **3.3 Test IP Restriction - Should Work**
- Uses IP restricted key with allowed IP (`127.0.0.1`)
- **Expected**: ✅ Request allowed (if IP validation enabled)

#### **3.4 Test IP Restriction - Should Block**
- Uses IP restricted key with blocked IP (`203.0.113.50`)
- **Expected**: 🚫 Request blocked with 403 (if IP validation enabled)

#### **3.5 Test Domain Restriction - Should Work**
- Uses domain restricted key with allowed domain (`localhost`)
- **Expected**: ✅ Request allowed (if domain validation enabled)

#### **3.6 Test Domain Restriction - Should Block**
- Uses domain restricted key with blocked domain (`unauthorized.com`)
- **Expected**: 🚫 Request blocked with 403 (if domain validation enabled)

#### **3.7 Test Invalid API Key**
- Uses completely invalid API key
- **Expected**: 🚫 Request blocked with 401

---

### **4. 📊 Analytics & Monitoring**
Test the new analytics features:

#### **4.1 Get Request Logs**
```
GET /api/v1/api-keys/analytics/{apiKeyId}/logs?page=0&size=10
```
**Expected Results:**
- ✅ List of all requests made with the API key
- ✅ **Automatically extracted IP addresses** (no user input needed)
- ✅ **Automatically extracted domains** (no user input needed)
- ✅ Request timestamps, methods, paths
- ✅ Response status codes and timing
- ✅ Security validation results (`isAllowedIp`, `isAllowedDomain`)

#### **4.2 Get Security Violations**
```
GET /api/v1/api-keys/analytics/{apiKeyId}/security-violations
```
**Expected Results:**
- ✅ List of requests that violated IP/Domain restrictions
- ✅ Details about blocked requests
- ✅ Violation timestamps and reasons

#### **4.3 Get Usage Statistics**
```
GET /api/v1/api-keys/analytics/{apiKeyId}/statistics?hours=24
```
**Expected Results:**
- ✅ Total request count for last 24 hours
- ✅ Number of security violations
- ✅ **Top client IPs** (automatically extracted)
- ✅ **Top domains** (automatically extracted)
- ✅ Time range information

#### **4.4 Get Geographic Distribution**
```
GET /api/v1/api-keys/analytics/{apiKeyId}/geographic-distribution?limit=20
```
**Expected Results:**
- ✅ Placeholder response (ready for GeoIP integration)

---

### **5. ⚡ Rate Limiting Tests**
Test rate limiting functionality:

#### **5.1-5.3 Rate Limit Test Sequence**
- Makes 3 consecutive requests with same API key
- **Expected Results:**
  - ✅ Rate limit headers in responses (`X-RateLimit-Limit`, `X-RateLimit-Remaining`)
  - ✅ Decreasing remaining count
  - ✅ 429 status when limit exceeded (if rate limiting active)

---

### **6. 👨‍💼 Admin Operations**
Test admin-only features:

#### **6.1 Get All API Keys (Admin)**
- **Expected**: ✅ List of all API keys in system

#### **6.2 Get System Statistics (Admin)**
- **Expected**: ✅ System-wide usage statistics

#### **6.3 Cleanup Old Logs (Admin)**
```
POST /api/v1/api-keys/analytics/cleanup?daysToKeep=30
```
- **Expected**: ✅ Confirmation of log cleanup

---

### **7. ❌ Error Scenarios**
Test error handling:

#### **7.1 Create API Key - Invalid Prefix**
- Uses prefix without dash (`invalid_prefix`)
- **Expected**: 🚫 400 Bad Request

#### **7.2 Create API Key - Invalid Scope**
- Uses non-existent scope (`INVALID_SCOPE`)
- **Expected**: 🚫 400 Bad Request

#### **7.3 Access Without Authentication**
- No JWT token provided
- **Expected**: 🚫 401 Unauthorized

---

## 🔧 **Configuration for Testing**

### **Enable Security Features (Optional)**
To test IP/Domain restrictions, add to `application.yml`:

```yaml
app:
  security:
    ip-validation:
      enabled: true     # Enable IP restrictions
    domain-validation:
      enabled: true     # Enable domain restrictions
```

### **Default Configuration (Recommended for Testing)**
```yaml
app:
  security:
    ip-validation:
      enabled: false    # Disabled by default
    domain-validation:
      enabled: false    # Disabled by default
  analytics:
    request-logging:
      enabled: true     # Always enabled
    async-logging: true # Non-blocking logging
```

---

## 📋 **What to Check During Testing**

### **✅ Database Tables**
After running the project, verify:
1. **`api_key_request_logs` table created** automatically
2. **Indexes created** for performance
3. **Request logs populated** after making API calls

### **✅ Automatic IP/Domain Extraction**
Check that logs contain:
1. **`client_ip`** - Automatically extracted from headers
2. **`domain`** - Automatically extracted from Host header
3. **`is_allowed_ip`** - Validation result
4. **`is_allowed_domain`** - Validation result

### **✅ Custom Prefixes**
Verify API keys have correct prefixes:
- Default: `sk-AbCdEf123...`
- Custom: `prod-AbCdEf123...`, `enterprise-AbCdEf123...`

### **✅ Analytics Data**
Check analytics endpoints return:
1. **Request logs** with all details
2. **Security violations** (if any restrictions violated)
3. **Usage statistics** with IP/domain breakdowns
4. **Proper pagination** for large datasets

---

## 🐛 **Common Issues & Solutions**

### **Issue 1: Security Restrictions Not Working**
**Cause**: IP/Domain validation disabled by default
**Solution**: Enable in configuration or test with validation disabled

### **Issue 2: No Request Logs**
**Cause**: Request logging might be disabled
**Solution**: Ensure `app.analytics.request-logging.enabled=true`

### **Issue 3: Empty Analytics**
**Cause**: No API requests made yet
**Solution**: Make some API requests first, then check analytics

### **Issue 4: Invalid Prefix Accepted**
**Cause**: Prefix validation might be too permissive
**Solution**: Check that prefix ends with `-`

---

## 📊 **Expected Test Results Summary**

### **✅ API Key Creation**
- ✅ Default prefix (`sk-`) works
- ✅ Custom prefixes work (`prod-`, `enterprise-`, etc.)
- ✅ IP restrictions stored correctly
- ✅ Domain restrictions stored correctly
- ✅ Scopes validated and stored

### **✅ Request Logging**
- ✅ Every API request logged automatically
- ✅ IP addresses extracted from headers
- ✅ Domains extracted from Host header
- ✅ Security validations performed
- ✅ Performance metrics captured

### **✅ Analytics**
- ✅ Request logs accessible via API
- ✅ Security violations tracked
- ✅ Usage statistics calculated
- ✅ Top IPs and domains identified

### **✅ Security**
- ✅ Invalid API keys rejected
- ✅ Unauthenticated requests blocked
- ✅ IP/Domain restrictions enforced (when enabled)
- ✅ Rate limiting functional

---

## 🎯 **Testing Checklist**

### **Before Testing:**
- [ ] Import updated Postman collection
- [ ] Import updated environment file
- [ ] Start the application (`mvn spring-boot:run`)
- [ ] Verify database connection

### **During Testing:**
- [ ] Run requests in order (User Management → API Keys → Usage → Analytics)
- [ ] Check console logs for automatic IP/domain extraction
- [ ] Verify environment variables are populated
- [ ] Check database for `api_key_request_logs` table and data

### **After Testing:**
- [ ] Verify all API keys created successfully
- [ ] Check analytics data is populated
- [ ] Confirm security features work as expected
- [ ] Review any error responses

---

## 📞 **Ready for Testing!**

The updated Postman collection includes **everything** needed to test all the new features:

1. **✅ Custom API key prefixes**
2. **✅ API key scopes**
3. **✅ IP address restrictions**
4. **✅ Domain restrictions**
5. **✅ Automatic request logging**
6. **✅ Comprehensive analytics**
7. **✅ Security monitoring**

**🚀 Import the collection and start testing! Let me know if you find any issues.**
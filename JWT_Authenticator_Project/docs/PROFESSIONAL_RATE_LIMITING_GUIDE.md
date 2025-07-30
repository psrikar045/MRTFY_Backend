# Professional Rate Limiting System - Complete Guide

This document explains the comprehensive, enterprise-grade rate limiting system implemented for API keys, following industry standards used by companies like AWS, Google, Stripe, and GitHub.

## 🎯 **System Overview**

### **Professional Approach vs. Your Original Idea**

#### **❌ Your Original Approach (Problematic):**
```java
// PROBLEMATIC: Decreasing counter without time windows
BASIC: 100 requests (count down: 100, 99, 98, 97...)
// Issues: No reset mechanism, no time windows, difficult to manage
```

#### **✅ Professional Approach (Industry Standard):**
```java
// PROFESSIONAL: Time-window based limits
BASIC: 100 requests per hour (resets every hour)
STANDARD: 200 requests per hour
PREMIUM: 400 requests per hour  
ENTERPRISE: 800 requests per hour
UNLIMITED: No limits
```

## 🏗️ **Architecture Components**

### **1. Rate Limit Tiers (Enum)**
```java
public enum RateLimitTier {
    BASIC("Basic", 100, 3600, "100 requests per hour"),
    STANDARD("Standard", 200, 3600, "200 requests per hour"),
    PREMIUM("Premium", 400, 3600, "400 requests per hour"),
    ENTERPRISE("Enterprise", 800, 3600, "800 requests per hour"),
    UNLIMITED("Unlimited", Integer.MAX_VALUE, 3600, "Unlimited requests");
}
```

### **2. Usage Statistics Entity**
```java
@Entity
public class ApiKeyUsageStats {
    private String apiKeyId;
    private RateLimitTier rateLimitTier;
    private LocalDateTime windowStart;    // Hour window start
    private LocalDateTime windowEnd;      // Hour window end
    private Integer requestCount;         // Current requests in window
    private Integer requestLimit;         // Max requests allowed
    private Integer remainingRequests;    // Remaining requests
    private Long totalRequestsLifetime;   // Total requests ever made
    private Integer blockedRequests;      // Blocked requests in window
    // ... more fields
}
```

### **3. Professional Rate Limit Service**
```java
@Service
public class ProfessionalRateLimitService {
    
    @Transactional
    public RateLimitResult checkRateLimit(String apiKeyId) {
        // 1. Get API key and tier
        // 2. Get/create current time window stats
        // 3. Check if limit exceeded
        // 4. Update statistics
        // 5. Return result with headers
    }
}
```

## 🔄 **How It Works**

### **Time Window Management**
```
Hour 1: 09:00-10:00 | Requests: 45/100 | Status: OK
Hour 2: 10:00-11:00 | Requests: 0/100  | Status: RESET (new window)
Hour 3: 11:00-12:00 | Requests: 100/100| Status: RATE LIMITED
```

### **Request Flow**
1. **Request arrives** with API key
2. **Authentication** validates API key
3. **Rate limit check** gets current window stats
4. **Window validation** checks if current window is valid
5. **Limit check** verifies if requests < limit
6. **Update stats** increments counters
7. **Return result** with rate limit headers

## 📊 **Statistics & Analytics**

### **Real-time Monitoring**
```json
{
  "apiKeyId": "550e8400-e29b-41d4-a716-446655440000",
  "currentUsage": {
    "requestCount": 45,
    "requestLimit": 100,
    "remainingRequests": 55,
    "usagePercentage": 45.0,
    "windowStart": "2024-01-15T10:00:00",
    "windowEnd": "2024-01-15T11:00:00"
  }
}
```

### **Historical Analytics**
```json
{
  "dailyRequestCounts": {
    "2024-01-14": 1250,
    "2024-01-15": 890
  },
  "hourlyUsagePattern": {
    "9": 120,  // 9 AM: 120 requests
    "10": 150, // 10 AM: 150 requests
    "14": 200  // 2 PM: 200 requests (peak)
  },
  "usageTrend": 1.5, // Increasing trend
  "peakUsageHour": 14
}
```

## 🚀 **API Endpoints**

### **1. Create API Key with Tier**
```bash
POST /api/v1/api-keys
{
  "name": "Production API Key",
  "description": "High-volume production key",
  "rateLimitTier": "PREMIUM",
  "expiresAt": null  // Will be set to 1 year from now
}

# Response
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Production API Key",
  "keyValue": "sk-1234567890abcdef...",  // Shown only once!
  "rateLimitTier": "PREMIUM",
  "expiresAt": "2025-01-15T12:00:00"     // Auto-set to 1 year
}
```

### **2. Use API Key (with Rate Limiting)**
```bash
POST /forward
Headers:
  X-API-Key: sk-1234567890abcdef...
  Content-Type: application/json

# Success Response Headers:
X-RateLimit-Limit: 400
X-RateLimit-Remaining: 355
X-RateLimit-Tier: PREMIUM
X-RateLimit-Reset: 2847  # Seconds until reset

# Rate Limited Response (429):
X-RateLimit-Limit: 400
X-RateLimit-Remaining: 0
X-RateLimit-Tier: PREMIUM
X-RateLimit-Reset: 1847
{
  "error": "Rate limit exceeded",
  "status": 429
}
```

### **3. Get Statistics**
```bash
# API Key Statistics
GET /api/v1/api-keys/statistics/{apiKeyId}?hours=24

# Usage Analytics
GET /api/v1/api-keys/statistics/{apiKeyId}/analytics?days=7

# System Statistics (Admin)
GET /api/v1/api-keys/statistics/system?hours=24

# Real-time Stats (Admin)
GET /api/v1/api-keys/statistics/realtime
```

## 💡 **Professional Features**

### **1. Automatic Expiration Handling**
```java
// In ApiKeyService.createApiKey()
LocalDateTime expirationDate = request.getExpiresAt();
if (expirationDate == null) {
    expirationDate = LocalDateTime.now().plusYears(1);
    log.info("Setting default expiration to: {}", expirationDate);
}
```

### **2. Dual Authentication Support**
```bash
# Method 1: JWT Token
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

# Method 2: API Key in Authorization Header  
Authorization: sk-1234567890abcdef...

# Method 3: API Key in X-API-Key Header
X-API-Key: sk-1234567890abcdef...
```

### **3. IP Restrictions**
```json
{
  "name": "Restricted Key",
  "allowedIps": ["192.168.1.100", "10.0.0.1"],
  "rateLimitTier": "ENTERPRISE"
}
```

### **4. Comprehensive Logging**
```
INFO: userId=DOMBR000001 | authMethod=API_KEY | tier=PREMIUM | requests=45/400 | url=https://api.example.com | status=200 | duration=1100ms
```

## 📈 **Advantages Over Original Approach**

### **✅ Professional Benefits:**

1. **Time-Window Based**
   - ✅ Automatic reset every hour
   - ✅ Predictable behavior
   - ✅ Industry standard approach

2. **Comprehensive Statistics**
   - ✅ Real-time monitoring
   - ✅ Historical analytics
   - ✅ Usage trends and patterns
   - ✅ Peak usage identification

3. **Enterprise Features**
   - ✅ Multiple rate limit tiers
   - ✅ IP restrictions
   - ✅ Scope-based permissions
   - ✅ Automatic expiration handling

4. **Scalability**
   - ✅ Database-backed (persistent)
   - ✅ Efficient queries with indexes
   - ✅ Cleanup mechanisms
   - ✅ Real-time monitoring

5. **Developer Experience**
   - ✅ Clear rate limit headers
   - ✅ Detailed error messages
   - ✅ Comprehensive documentation
   - ✅ Multiple authentication methods

### **❌ Issues with Original Approach:**

1. **No Time Windows**
   - ❌ When do limits reset?
   - ❌ How to handle burst traffic?
   - ❌ Unpredictable behavior

2. **Decreasing Counter Problems**
   - ❌ Complex reset logic
   - ❌ Race conditions
   - ❌ Difficult to implement sliding windows

3. **Limited Statistics**
   - ❌ No historical data
   - ❌ No usage patterns
   - ❌ No trend analysis

## 🎯 **Usage Examples**

### **Basic Usage Flow**
```bash
# 1. Create API key
curl -X POST http://localhost:8080/api/v1/api-keys \
  -H "Authorization: Bearer <jwt>" \
  -d '{"name": "Test Key", "rateLimitTier": "BASIC"}'

# 2. Use API key (within limits)
curl -X POST http://localhost:8080/forward \
  -H "X-API-Key: sk-abc123..." \
  -d '{"url": "https://api.example.com"}'
# Response: 200 OK with rate limit headers

# 3. Exceed rate limit
# After 100 requests in the hour...
curl -X POST http://localhost:8080/forward \
  -H "X-API-Key: sk-abc123..." \
  -d '{"url": "https://api.example.com"}'
# Response: 429 Too Many Requests

# 4. Check statistics
curl -X GET http://localhost:8080/api/v1/api-keys/statistics/sk-abc123
```

### **Advanced Analytics**
```bash
# Get 7-day usage analytics
curl -X GET "http://localhost:8080/api/v1/api-keys/statistics/sk-abc123/analytics?days=7"

# Response includes:
# - Daily request counts
# - Hourly usage patterns  
# - Peak usage times
# - Usage trends
# - Success rates
```

## 🔧 **Configuration & Customization**

### **Custom Rate Limit Tiers**
```java
// Add new tier to enum
ENTERPRISE_PLUS("Enterprise Plus", 1600, 3600, "1600 requests per hour"),
```

### **Different Time Windows**
```java
// 15-minute windows instead of hourly
BASIC("Basic", 25, 900, "25 requests per 15 minutes"),
```

### **Custom Window Logic**
```java
// Daily limits instead of hourly
LocalDateTime windowStart = now.truncatedTo(ChronoUnit.DAYS);
LocalDateTime windowEnd = windowStart.plusDays(1);
```

## 🚨 **Best Practices**

### **For API Key Creation:**
1. **Always set appropriate tiers** based on user needs
2. **Use meaningful names** for tracking
3. **Set expiration dates** for security
4. **Apply IP restrictions** for production keys

### **For Rate Limiting:**
1. **Monitor usage patterns** regularly
2. **Adjust tiers** based on actual usage
3. **Clean up old statistics** periodically
4. **Set up alerts** for high usage

### **For Statistics:**
1. **Use real-time monitoring** for operational insights
2. **Analyze trends** for capacity planning
3. **Track success rates** for system health
4. **Monitor peak usage** for scaling decisions

## 🎉 **Conclusion**

This professional rate limiting system provides:

- ✅ **Industry-standard time-window approach**
- ✅ **Comprehensive statistics and analytics**
- ✅ **Enterprise-grade features**
- ✅ **Scalable and maintainable architecture**
- ✅ **Developer-friendly APIs**
- ✅ **Real-time monitoring capabilities**

The system follows patterns used by major API providers and provides a solid foundation for scaling to millions of API requests while maintaining performance and providing detailed insights into usage patterns.
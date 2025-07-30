# 🌐 Automatic IP and Domain Extraction Demo

## 📋 **How We Extract IP and Domain WITHOUT User Input**

### **🔍 Automatic IP Address Extraction**

The system automatically extracts the client's IP address from HTTP request headers **without requiring any user input**:

#### **1. Header Priority Order**
```java
// We check these headers in order:
String[] headerNames = {
    "X-Forwarded-For",      // Most common proxy header
    "X-Real-IP",            // Nginx proxy header  
    "X-Client-IP",          // Generic client IP header
    "CF-Connecting-IP",     // Cloudflare header
    "True-Client-IP",       // Akamai header
    "X-Cluster-Client-IP"   // Cluster/load balancer header
};
```

#### **2. Extraction Process**
```java
public String extractClientIp(HttpServletRequest request) {
    // Check proxy headers first
    for (String headerName : headerNames) {
        String ip = request.getHeader(headerName);
        if (ip != null && !ip.trim().isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // Handle comma-separated IPs (X-Forwarded-For: "client, proxy1, proxy2")
            if (ip.contains(",")) {
                ip = ip.split(",")[0].trim(); // Take the first (original client) IP
            }
            if (isValidIpAddress(ip)) {
                return ip; // Return the first valid IP found
            }
        }
    }
    
    // Fallback to direct connection IP
    String remoteAddr = request.getRemoteAddr();
    return isValidIpAddress(remoteAddr) ? remoteAddr : null;
}
```

#### **3. Real-World Examples**

**Direct Connection:**
```
Request from browser → Your server
remoteAddr = "203.0.113.45"
Result: "203.0.113.45"
```

**Through Proxy/Load Balancer:**
```
Client (203.0.113.45) → Nginx Proxy (10.0.0.1) → Your server
X-Forwarded-For: "203.0.113.45"
X-Real-IP: "203.0.113.45"
remoteAddr: "10.0.0.1"
Result: "203.0.113.45" (from X-Forwarded-For)
```

**Through Cloudflare:**
```
Client (203.0.113.45) → Cloudflare → Your server
CF-Connecting-IP: "203.0.113.45"
X-Forwarded-For: "203.0.113.45"
Result: "203.0.113.45" (from CF-Connecting-IP)
```

**Multiple Proxies:**
```
Client → Proxy1 → Proxy2 → Your server
X-Forwarded-For: "203.0.113.45, 10.0.0.1, 10.0.0.2"
Result: "203.0.113.45" (first IP in the chain)
```

---

### **🌐 Automatic Domain Extraction**

The system automatically extracts the domain from the HTTP `Host` header:

#### **1. Extraction Process**
```java
public String extractDomain(HttpServletRequest request) {
    String host = request.getHeader("Host");
    if (host == null || host.trim().isEmpty()) {
        return null;
    }
    
    // Remove port if present (example.com:8080 → example.com)
    if (host.contains(":")) {
        host = host.split(":")[0];
    }
    
    return host.toLowerCase().trim();
}
```

#### **2. Real-World Examples**

**Standard Request:**
```
GET /api/v1/users HTTP/1.1
Host: api.myapp.com
Result: "api.myapp.com"
```

**With Port:**
```
GET /api/v1/users HTTP/1.1
Host: api.myapp.com:8080
Result: "api.myapp.com" (port removed)
```

**Localhost Development:**
```
GET /api/v1/users HTTP/1.1
Host: localhost:8080
Result: "localhost"
```

**Custom Domain:**
```
GET /api/v1/users HTTP/1.1
Host: custom-api.company.com
Result: "custom-api.company.com"
```

---

## 🗄️ **Database Table Creation**

### **✅ Automatic Table Creation**

When you run the project, the `api_key_request_logs` table will be **automatically created** because:

1. **JPA Entity**: The `ApiKeyRequestLog` class is annotated with `@Entity`
2. **Auto DDL**: Spring Boot's `spring.jpa.hibernate.ddl-auto` setting handles table creation
3. **Proper Mapping**: All fields are properly mapped with `@Column` annotations

### **📊 Table Schema**
```sql
CREATE TABLE api_key_request_logs (
    id UUID PRIMARY KEY,
    api_key_id UUID NOT NULL,
    user_fk_id VARCHAR(11) NOT NULL,
    client_ip VARCHAR(45),           -- Automatically extracted
    domain VARCHAR(255),             -- Automatically extracted
    user_agent TEXT,
    request_method VARCHAR(10),
    request_path VARCHAR(500),
    request_timestamp TIMESTAMP NOT NULL,
    response_status INTEGER,
    response_time_ms BIGINT,
    request_size_bytes BIGINT,
    response_size_bytes BIGINT,
    rate_limit_tier VARCHAR(50),
    rate_limit_remaining INTEGER,
    addon_used BOOLEAN,
    country_code VARCHAR(2),
    region VARCHAR(100),
    city VARCHAR(100),
    error_message TEXT,
    is_allowed_ip BOOLEAN,           -- Validation result
    is_allowed_domain BOOLEAN        -- Validation result
);

-- Indexes for performance
CREATE INDEX idx_api_key_id ON api_key_request_logs(api_key_id);
CREATE INDEX idx_request_timestamp ON api_key_request_logs(request_timestamp);
CREATE INDEX idx_client_ip ON api_key_request_logs(client_ip);
CREATE INDEX idx_domain ON api_key_request_logs(domain);
CREATE INDEX idx_api_key_timestamp ON api_key_request_logs(api_key_id, request_timestamp);
```

---

## 🔄 **Automatic Logging Process**

### **1. Request Arrives**
```
Client Request → DynamicAuthenticationFilter
```

### **2. Authentication & Extraction**
```java
// In DynamicAuthenticationFilter.doFilterInternal()
AuthenticationResult authResult = authStrategy.authenticate(request);

if (authResult.isSuccess() && authResult.getApiKey() != null) {
    // Automatically extract IP and domain
    String clientIp = requestLogService.extractClientIp(request);
    String domain = requestLogService.extractDomain(request);
    
    // Validate restrictions (if enabled)
    boolean ipAllowed = requestLogService.validateClientIp(authResult.getApiKey(), clientIp);
    boolean domainAllowed = requestLogService.validateDomain(authResult.getApiKey(), domain);
}
```

### **3. Automatic Logging**
```java
// Log the request asynchronously (non-blocking)
requestLogService.logRequestAsync(request, authResult.getApiKey(), 200, responseTime);
```

### **4. Database Record Created**
```java
ApiKeyRequestLog logEntry = ApiKeyRequestLog.builder()
    .apiKeyId(apiKey.getId())
    .userFkId(apiKey.getUserFkId())
    .clientIp(clientIp)              // Automatically extracted
    .domain(domain)                  // Automatically extracted
    .userAgent(request.getHeader("User-Agent"))
    .requestMethod(request.getMethod())
    .requestPath(request.getRequestURI())
    .requestTimestamp(LocalDateTime.now())
    .responseStatus(responseStatus)
    .isAllowedIp(ipAllowed)
    .isAllowedDomain(domainAllowed)
    .build();

requestLogRepository.save(logEntry);
```

---

## 🧪 **Testing the Extraction**

### **1. Start the Application**
```bash
mvn spring-boot:run
```

### **2. Make API Request**
```bash
curl -H "X-API-Key: sk-your-api-key" \
     -H "Host: api.myapp.com" \
     http://localhost:8080/api/v1/users
```

### **3. Check Database**
```sql
SELECT client_ip, domain, request_timestamp, is_allowed_ip, is_allowed_domain 
FROM api_key_request_logs 
ORDER BY request_timestamp DESC 
LIMIT 5;
```

**Expected Result:**
```
client_ip    | domain        | request_timestamp   | is_allowed_ip | is_allowed_domain
-------------|---------------|--------------------|--------------|-----------------
127.0.0.1    | api.myapp.com | 2024-01-15 10:30:00| true         | true
```

---

## 📈 **View Analytics**

### **Get Request Logs**
```bash
GET /api/v1/api-keys/analytics/{apiKeyId}/logs
Authorization: Bearer JWT_TOKEN
```

**Response:**
```json
{
  "logs": [
    {
      "clientIp": "203.0.113.45",      // Automatically extracted
      "domain": "api.myapp.com",       // Automatically extracted
      "userAgent": "curl/7.68.0",
      "requestMethod": "GET",
      "requestPath": "/api/v1/users",
      "requestTimestamp": "2024-01-15T10:30:00",
      "isAllowedIp": true,
      "isAllowedDomain": true
    }
  ]
}
```

---

## ⚙️ **Configuration**

### **Enable/Disable Features**
```yaml
# application.yml
app:
  security:
    ip-validation:
      enabled: false    # IP validation disabled by default
    domain-validation:
      enabled: false    # Domain validation disabled by default
  analytics:
    request-logging:
      enabled: true     # Logging always enabled
    async-logging: true # Non-blocking logging
```

### **Why Disabled by Default?**
- ✅ **Backward Compatibility**: Existing API keys work without restrictions
- ✅ **Gradual Rollout**: Enable when you're ready to enforce restrictions
- ✅ **Data Collection**: Still logs IP/domain for analytics even when validation is disabled

---

## 🎯 **Key Points**

### **✅ Completely Automatic**
- **No user input required** - IP and domain are extracted from HTTP headers
- **Works with all proxy setups** - Handles Nginx, Cloudflare, load balancers
- **Fallback mechanisms** - Multiple extraction methods ensure reliability

### **✅ Production Ready**
- **Async logging** - Doesn't slow down API responses
- **Proper indexing** - Fast analytics queries
- **Error handling** - Graceful failure if extraction fails

### **✅ Privacy Friendly**
- **Configurable** - Can disable logging if needed
- **Retention policies** - Automatic cleanup of old logs
- **Secure storage** - Proper database security practices

**🚀 The system automatically captures and analyzes all request patterns without any manual intervention!**
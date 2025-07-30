# MRTFY API - Complete Developer Documentation

## 📋 **Table of Contents**

1. [Quick Start](#-quick-start)
2. [Authentication Methods](#-authentication-methods)
3. [Dynamic Authentication System](#-dynamic-authentication-system)
4. [API Endpoints](#-api-endpoints)
5. [Rate Limiting & Add-ons](#-rate-limiting--add-ons)
6. [Code Examples](#-code-examples)
7. [Testing with Postman](#-testing-with-postman)
8. [Error Handling](#-error-handling)
9. [Best Practices](#-best-practices)
10. [Deployment Guide](#-deployment-guide)

---

## 🚀 **Quick Start**

### **1. Get Your API Key**

```bash
# Step 1: Register and login
curl -X POST https://api.mrtfy.com/v1/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "developer",
    "email": "dev@company.com",
    "password": "SecurePass123!",
    "firstName": "Developer",
    "lastName": "Name"
  }'

# Step 2: Login to get JWT token
curl -X POST https://api.mrtfy.com/v1/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "developer",
    "password": "SecurePass123!"
  }'

# Step 3: Create API key with JWT token
curl -X POST https://api.mrtfy.com/v1/api/v1/api-keys \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Production API Key",
    "rateLimitTier": "BASIC",
    "description": "Main API key for production use"
  }'
```

### **2. Make Your First API Call**

```bash
# Test your API key
curl -X GET https://api.mrtfy.com/v1/api/v1/users \
  -H "X-API-Key: sk-your-api-key-here"

# Forward a request through the gateway
curl -X POST https://api.mrtfy.com/v1/forward \
  -H "X-API-Key: sk-your-api-key-here" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://jsonplaceholder.typicode.com/users/1",
    "method": "GET",
    "headers": {"Accept": "application/json"}
  }'
```

---

## 🔐 **Authentication Methods**

### **Method 1: API Key Authentication (Recommended)**

```bash
# Using X-API-Key header (preferred)
curl -H "X-API-Key: sk-1234567890abcdef..." https://api.mrtfy.com/v1/api/v1/users

# Using Authorization header
curl -H "Authorization: sk-1234567890abcdef..." https://api.mrtfy.com/v1/api/v1/users
```

### **Method 2: JWT Bearer Token**

```bash
# For user management endpoints
curl -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
     https://api.mrtfy.com/v1/api/v1/api-keys
```

### **Authentication Headers Summary**

| Method | Header | Format | Use Case |
|--------|--------|--------|----------|
| **API Key** | `X-API-Key` | `sk-1234567890abcdef...` | Application requests |
| **API Key** | `Authorization` | `sk-1234567890abcdef...` | Alternative format |
| **JWT** | `Authorization` | `Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...` | User management |

---

## ⚙️ **Dynamic Authentication System**

### **Configuration-Driven Authentication**

The MRTFY API uses a revolutionary dynamic authentication system that allows switching between authentication methods without code changes.

#### **Environment Configuration**

```yaml
# application.yml
app:
  auth:
    method: both                    # 'api_key', 'jwt', 'both', 'jwt_first'
    api-key-header: X-API-Key       # Configurable header name
    jwt-header: Authorization       # JWT header name
    fallback: true                  # Allow fallback between methods
    require-auth: true              # Require authentication
    detailed-errors: true           # Show detailed error info
```

#### **Supported Authentication Strategies**

| Strategy | Description | Use Case |
|----------|-------------|----------|
| `api_key` | Only API key authentication | Microservices, API-only apps |
| `jwt` | Only JWT authentication | Web applications |
| `both` | API key first, JWT fallback | Mixed environments |
| `jwt_first` | JWT first, API key fallback | Migration scenarios |

#### **Environment-Specific Configurations**

```bash
# Development - Allow both methods, detailed errors
export SPRING_PROFILES_ACTIVE=dev

# Production - API key only, minimal errors  
export SPRING_PROFILES_ACTIVE=prod

# Migration - JWT preferred, API key fallback
export SPRING_PROFILES_ACTIVE=migration

# Testing - Both methods, comprehensive logging
export SPRING_PROFILES_ACTIVE=test
```

### **Benefits of Dynamic Authentication**

- ✅ **Zero Downtime**: Switch auth methods without redeployment
- ✅ **Environment Specific**: Different auth strategies per environment
- ✅ **Gradual Migration**: Smooth transition between auth methods
- ✅ **Reduced Maintenance**: Single codebase for multiple auth strategies
- ✅ **Future Proof**: Easy to add new authentication methods

---

## 📡 **API Endpoints**

### **🔑 API Key Management**

#### **Create API Key**
```bash
POST /api/v1/api-keys
Authorization: Bearer JWT_TOKEN
Content-Type: application/json

{
  "name": "Production API Key",
  "description": "Main API key for production",
  "prefix": "prod-",                    # Custom prefix (optional, defaults to "sk-")
  "rateLimitTier": "PREMIUM",
  "allowedIps": ["192.168.1.100", "10.0.0.0/24"],  # IP restrictions (optional)
  "allowedDomains": ["myapp.com", "*.myapp.com"],   # Domain restrictions (optional)
  "scopes": ["READ", "WRITE", "ADMIN"],             # API scopes (optional)
  "expiresAt": "2025-12-31T23:59:59"
}
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Production API Key",
  "apiKey": "prod-AbCdEf123456789...",  # Full API key with custom prefix
  "prefix": "prod-",
  "rateLimitTier": "PREMIUM",
  "allowedIps": ["192.168.1.100", "10.0.0.0/24"],
  "allowedDomains": ["myapp.com", "*.myapp.com"],
  "scopes": ["READ", "WRITE", "ADMIN"],
  "isActive": true,
  "expiresAt": "2025-12-31T23:59:59",
  "createdAt": "2024-01-15T10:30:00"
}
```

#### **List API Keys**
```bash
GET /api/v1/api-keys?page=1&size=20&active=true
Authorization: Bearer JWT_TOKEN
```

#### **Get API Key Details**
```bash
GET /api/v1/api-keys/{apiKeyId}
Authorization: Bearer JWT_TOKEN
```

#### **Update API Key**
```bash
PUT /api/v1/api-keys/{apiKeyId}
Authorization: Bearer JWT_TOKEN
Content-Type: application/json

{
  "name": "Updated API Key Name",
  "rateLimitTier": "ENTERPRISE",
  "isActive": true
}
```

#### **Delete API Key**
```bash
DELETE /api/v1/api-keys/{apiKeyId}
Authorization: Bearer JWT_TOKEN
```

### **📊 Add-on Management**

#### **Get Available Packages**
```bash
GET /api/v1/api-keys/addons/packages
X-API-Key: sk-your-api-key
```

#### **Purchase Add-on**
```bash
POST /api/v1/api-keys/addons/purchase
Authorization: Bearer JWT_TOKEN
Content-Type: application/json

{
  "apiKeyId": "550e8400-e29b-41d4-a716-446655440000",
  "addOnPackage": "ADDON_MEDIUM",
  "durationMonths": 1,
  "autoRenew": true,
  "reason": "Scaling for product launch"
}
```

#### **Get Recommendations**
```bash
GET /api/v1/api-keys/addons/{apiKeyId}/recommendations?overageRequests=150
X-API-Key: sk-your-api-key
```

#### **Get Active Add-ons**
```bash
GET /api/v1/api-keys/addons/{apiKeyId}/active
X-API-Key: sk-your-api-key
```

### **📈 Analytics & Statistics**

#### **Get Usage Statistics**
```bash
GET /api/v1/api-keys/statistics/{apiKeyId}?hours=24
X-API-Key: sk-your-api-key
```

#### **Get Request Logs**
```bash
GET /api/v1/api-keys/analytics/{apiKeyId}/logs?page=0&size=20
Authorization: Bearer JWT_TOKEN
```

**Response:**
```json
{
  "logs": [
    {
      "id": "log-uuid",
      "clientIp": "192.168.1.100",
      "domain": "myapp.com",
      "userAgent": "MyApp/1.0",
      "requestMethod": "GET",
      "requestPath": "/api/v1/users",
      "requestTimestamp": "2024-01-15T10:30:00",
      "responseStatus": 200,
      "responseTimeMs": 45,
      "isAllowedIp": true,
      "isAllowedDomain": true,
      "rateLimitTier": "PREMIUM"
    }
  ],
  "totalElements": 1250,
  "totalPages": 63,
  "currentPage": 0
}
```

#### **Get Security Violations**
```bash
GET /api/v1/api-keys/analytics/{apiKeyId}/security-violations
Authorization: Bearer JWT_TOKEN
```

#### **Get Geographic Distribution**
```bash
GET /api/v1/api-keys/analytics/{apiKeyId}/geographic-distribution?limit=20
Authorization: Bearer JWT_TOKEN
```

#### **Get System Statistics (Admin)**
```bash
GET /api/v1/api-keys/statistics/system?hours=168
Authorization: Bearer JWT_TOKEN
```

### **🔍 Advanced Analytics**

#### **Monitor Security Violations**
```bash
GET /api/v1/api-keys/analytics/{apiKeyId}/security-violations
Authorization: Bearer JWT_TOKEN
```

**Response:**
```json
{
  "violations": [
    {
      "clientIp": "203.0.113.50",
      "domain": "unauthorized.com",
      "requestTimestamp": "2024-01-15T10:30:00",
      "isAllowedIp": false,
      "isAllowedDomain": false,
      "errorMessage": "Access denied: IP/Domain restriction violation"
    }
  ],
  "totalViolations": 5
}
```

#### **Get Comprehensive Usage Statistics**
```bash
GET /api/v1/api-keys/analytics/{apiKeyId}/statistics?hours=24
Authorization: Bearer JWT_TOKEN
```

**Response:**
```json
{
  "timeRange": {
    "hours": 24,
    "since": "2024-01-14T10:30:00",
    "until": "2024-01-15T10:30:00"
  },
  "requestCount": 1250,
  "securityViolations": 3,
  "topClientIps": [
    {"ip": "192.168.1.100", "requestCount": 800},
    {"ip": "10.0.0.50", "requestCount": 300}
  ],
  "topDomains": [
    {"domain": "myapp.com", "requestCount": 1000},
    {"domain": "api.myapp.com", "requestCount": 250}
  ]
}
```

#### **Clean Up Old Logs (Admin)**
```bash
POST /api/v1/api-keys/analytics/cleanup?daysToKeep=90
Authorization: Bearer JWT_TOKEN
```

### **👥 Sample Protected Endpoints**

#### **Users Management**
```bash
# Get users
GET /api/v1/users?page=1&limit=20&search=john
X-API-Key: sk-your-api-key

# Create user
POST /api/v1/users
X-API-Key: sk-your-api-key
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "role": "user",
  "permissions": ["read", "write"]
}
```

#### **Data Operations**
```bash
# Fetch data
GET /api/v1/data/fetch?type=analytics&startDate=2024-01-01&endDate=2024-01-31
X-API-Key: sk-your-api-key

# Submit data
POST /api/v1/data/fetch
X-API-Key: sk-your-api-key
Content-Type: application/json

{
  "type": "analytics",
  "data": {
    "events": [
      {
        "name": "page_view",
        "timestamp": "2024-01-15T10:30:00Z",
        "properties": {
          "page": "/dashboard",
          "user_id": "user123"
        }
      }
    ]
  },
  "options": {
    "async": true,
    "notify": true
  }
}
```

### **🌐 API Gateway**

#### **Forward Requests**
```bash
POST /forward
X-API-Key: sk-your-api-key
Content-Type: application/json

{
  "url": "https://api.target.com/endpoint",
  "method": "POST",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer target-api-token"
  },
  "body": {
    "data": "to_forward"
  },
  "timeout": 30
}
```

---

## 🔒 **Advanced Security Features**

### **Custom API Key Prefixes**

You can specify custom prefixes for your API keys to organize them by environment or purpose:

```bash
# Create API key with custom prefix
POST /api/v1/api-keys
{
  "name": "Production API Key",
  "prefix": "prod-",        # Results in: prod-AbCdEf123456789...
  "rateLimitTier": "PREMIUM"
}

# Create API key with environment prefix
POST /api/v1/api-keys
{
  "name": "Staging API Key", 
  "prefix": "staging-",     # Results in: staging-XyZ987654321...
  "rateLimitTier": "BASIC"
}

# No prefix specified - uses default "sk-"
POST /api/v1/api-keys
{
  "name": "Default API Key"  # Results in: sk-AbCdEf123456789...
}
```

**Supported Default Prefixes:**
- `sk-` - Standard secret key (default)
- `admin-` - Administrative keys
- `biz-` - Business keys
- Custom prefixes - Any string ending with `-`

### **IP Address Restrictions**

Restrict API key usage to specific IP addresses or CIDR ranges:

```bash
POST /api/v1/api-keys
{
  "name": "Restricted API Key",
  "allowedIps": [
    "192.168.1.100",        # Specific IP
    "10.0.0.0/24",          # CIDR range (future support)
    "203.0.113.0/24"        # Another CIDR range
  ]
}
```

**IP Validation Features:**
- ✅ **Exact IP matching** - Currently supported
- 🔄 **CIDR range support** - Planned for future release
- ✅ **Proxy header detection** - X-Forwarded-For, X-Real-IP, CF-Connecting-IP
- ✅ **Security violation logging** - All blocked requests are logged

### **Domain Restrictions**

Restrict API key usage to specific domains:

```bash
POST /api/v1/api-keys
{
  "name": "Domain Restricted Key",
  "allowedDomains": [
    "myapp.com",            # Exact domain
    "*.myapp.com",          # Wildcard subdomains
    "api.partner.com"       # Partner domain
  ]
}
```

**Domain Validation Features:**
- ✅ **Exact domain matching**
- ✅ **Wildcard subdomain support** - `*.example.com`
- ✅ **Host header validation**
- ✅ **Security violation logging**

### **API Key Scopes**

Control what actions an API key can perform:

```bash
POST /api/v1/api-keys
{
  "name": "Limited Scope Key",
  "scopes": [
    "READ",                 # Read-only access
    "WRITE",                # Write access
    "ADMIN",                # Administrative access
    "ANALYTICS"             # Analytics access
  ]
}
```

**Available Scopes:**
- `READ` - Read operations (GET requests)
- `WRITE` - Write operations (POST, PUT, PATCH)
- `DELETE` - Delete operations
- `ADMIN` - Administrative functions
- `ANALYTICS` - Access to analytics endpoints

### **Request Logging & Analytics**

Every API request is automatically logged for analytics and security monitoring:

**Logged Information:**
- Client IP address and domain
- Request method, path, and timestamp
- Response status and timing
- User agent and geographic location
- Rate limit usage and add-on consumption
- Security violations (IP/Domain restrictions)

**Configuration:**
```yaml
app:
  security:
    ip-validation:
      enabled: false          # Enable IP validation (disabled by default)
    domain-validation:
      enabled: false          # Enable domain validation (disabled by default)
  analytics:
    request-logging:
      enabled: true           # Enable request logging
    async-logging: true       # Log requests asynchronously
```

---

## 📊 **Rate Limiting & Add-ons**

### **Rate Limit Tiers**

| Tier | Daily Limit | Price | Features |
|------|-------------|-------|----------|
| **BASIC** | 100 requests | FREE | Perfect for testing |
| **STANDARD** | 500 requests | $10/month | Small applications |
| **PREMIUM** | 2,000 requests | $50/month | Growing businesses |
| **ENTERPRISE** | 10,000 requests | $200/month | Large applications |
| **UNLIMITED** | No limits | $500/month | Enterprise solutions |

### **Add-on Packages**

| Package | Additional Requests | Price | Best For |
|---------|-------------------|-------|----------|
| **Small** | +100/day | $5/month | Occasional spikes |
| **Medium** | +500/day | $20/month | Regular overages |
| **Large** | +2,000/day | $75/month | High-volume periods |
| **Enterprise** | +10,000/day | $300/month | Massive scaling |

### **Rate Limit Headers**

Every API response includes rate limit information:

```http
X-RateLimit-Limit: 100                    # Your daily limit
X-RateLimit-Remaining: 45                 # Requests remaining today
X-RateLimit-Tier: BASIC                   # Your current tier
X-RateLimit-Additional-Available: 100     # Add-on requests available
X-RateLimit-Total-Remaining: 145          # Total requests remaining
X-RateLimit-Reset: 43200                  # Seconds until reset
X-RateLimit-Used-AddOn: false             # Whether add-on was used
```

### **Automatic Add-on Usage**

When your base tier limit is exceeded, add-ons are used automatically:

```http
# Request using add-on (base limit exceeded)
HTTP/1.1 200 OK
X-RateLimit-Limit: 100                    # Base tier limit
X-RateLimit-Remaining: 0                  # Base tier exhausted
X-RateLimit-Additional-Available: 99      # Add-on requests remaining
X-RateLimit-Total-Remaining: 99           # Total available
X-RateLimit-Used-AddOn: true              # Add-on was used ✅
```

---

## 💻 **Code Examples**

### **JavaScript/Node.js**

```javascript
const axios = require('axios');

class MRTFYClient {
  constructor(apiKey, baseURL = 'https://api.mrtfy.com/v1') {
    this.apiKey = apiKey;
    this.baseURL = baseURL;
    this.client = axios.create({
      baseURL: this.baseURL,
      headers: {
        'X-API-Key': this.apiKey,
        'Content-Type': 'application/json'
      }
    });

    // Add response interceptor for rate limit handling
    this.client.interceptors.response.use(
      response => {
        this.logRateLimitInfo(response.headers);
        return response;
      },
      error => {
        if (error.response?.status === 429) {
          console.log('Rate limit exceeded:', error.response.data.details);
        }
        throw error;
      }
    );
  }

  logRateLimitInfo(headers) {
    const limit = headers['x-ratelimit-limit'];
    const remaining = headers['x-ratelimit-remaining'];
    const tier = headers['x-ratelimit-tier'];
    const usedAddOn = headers['x-ratelimit-used-addon'];

    if (limit) {
      console.log(`Rate Limit: ${remaining}/${limit} (${tier})`);
      if (usedAddOn === 'true') {
        console.log('✅ Add-on request used');
      }
    }
  }

  async forwardRequest(url, method = 'GET', data = null) {
    const payload = {
      url,
      method,
      headers: { 'Accept': 'application/json' }
    };

    if (data) {
      payload.body = data;
    }

    const response = await this.client.post('/forward', payload);
    return response.data;
  }

  async getUsers(page = 1, limit = 20) {
    const response = await this.client.get(`/api/v1/users?page=${page}&limit=${limit}`);
    return response.data;
  }

  async createUser(userData) {
    const response = await this.client.post('/api/v1/users', userData);
    return response.data;
  }

  async getUsageStats(apiKeyId) {
    const response = await this.client.get(`/api/v1/api-keys/statistics/${apiKeyId}`);
    return response.data;
  }

  async purchaseAddOn(apiKeyId, packageType, options = {}) {
    const payload = {
      apiKeyId,
      addOnPackage: packageType,
      durationMonths: options.duration || 1,
      autoRenew: options.autoRenew || false,
      reason: options.reason || 'Additional capacity needed'
    };

    const response = await this.client.post('/api/v1/api-keys/addons/purchase', payload);
    return response.data;
  }
}

// Usage
const client = new MRTFYClient('sk-your-api-key-here');

// Forward a request
client.forwardRequest('https://jsonplaceholder.typicode.com/users/1')
  .then(data => console.log('Forwarded response:', data))
  .catch(err => console.error('Error:', err.message));

// Get users
client.getUsers(1, 10)
  .then(users => console.log(`Retrieved ${users.data.length} users`))
  .catch(err => console.error('Error:', err.message));

// Purchase add-on when needed
client.purchaseAddOn('your-api-key-id', 'ADDON_SMALL', {
  duration: 1,
  autoRenew: true,
  reason: 'Traffic spike expected'
}).then(addon => console.log('Add-on purchased:', addon.id));
```

### **Python**

```python
import requests
import os
from typing import Optional, Dict, Any
from datetime import datetime

class MRTFYClient:
    def __init__(self, api_key: str = None, base_url: str = 'https://api.mrtfy.com/v1'):
        self.api_key = api_key or os.getenv('MRTFY_API_KEY')
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update({
            'X-API-Key': self.api_key,
            'Content-Type': 'application/json'
        })

    def _log_rate_limit_info(self, headers: Dict[str, str]):
        """Log rate limit information from response headers"""
        limit = headers.get('X-RateLimit-Limit')
        remaining = headers.get('X-RateLimit-Remaining')
        tier = headers.get('X-RateLimit-Tier')
        used_addon = headers.get('X-RateLimit-Used-AddOn')

        if limit:
            print(f"Rate Limit: {remaining}/{limit} ({tier})")
            if used_addon == 'true':
                print("✅ Add-on request used")

    def _handle_response(self, response: requests.Response) -> Dict[str, Any]:
        """Handle API response with rate limit logging and error handling"""
        self._log_rate_limit_info(response.headers)

        if response.status_code == 429:
            rate_limit_info = response.json()
            print(f"Rate limit exceeded. Recommendations: {rate_limit_info['details']['recommendations']}")
            
        response.raise_for_status()
        return response.json()

    def forward_request(self, url: str, method: str = 'GET', 
                       data: Optional[Dict] = None, headers: Optional[Dict] = None) -> Dict[str, Any]:
        """Forward a request through MRTFY gateway"""
        payload = {
            'url': url,
            'method': method,
            'headers': headers or {'Accept': 'application/json'}
        }
        
        if data:
            payload['body'] = data
            
        response = self.session.post(f'{self.base_url}/forward', json=payload)
        return self._handle_response(response)

    def get_users(self, page: int = 1, limit: int = 20, search: str = '') -> Dict[str, Any]:
        """Get users list with pagination and search"""
        params = {'page': page, 'limit': limit}
        if search:
            params['search'] = search
            
        response = self.session.get(f'{self.base_url}/api/v1/users', params=params)
        return self._handle_response(response)

    def create_user(self, user_data: Dict[str, Any]) -> Dict[str, Any]:
        """Create a new user"""
        response = self.session.post(f'{self.base_url}/api/v1/users', json=user_data)
        return self._handle_response(response)

    def submit_data(self, data_type: str, events: list, options: Optional[Dict] = None) -> Dict[str, Any]:
        """Submit data for processing"""
        payload = {
            'type': data_type,
            'data': {'events': events},
            'options': options or {'async': True, 'notify': True}
        }
        
        response = self.session.post(f'{self.base_url}/api/v1/data/fetch', json=payload)
        return self._handle_response(response)

    def get_usage_stats(self, api_key_id: str, hours: int = 24) -> Dict[str, Any]:
        """Get API usage statistics"""
        response = self.session.get(
            f'{self.base_url}/api/v1/api-keys/statistics/{api_key_id}',
            params={'hours': hours}
        )
        return self._handle_response(response)

    def get_addon_recommendations(self, api_key_id: str, overage_requests: int = 0) -> Dict[str, Any]:
        """Get add-on package recommendations"""
        response = self.session.get(
            f'{self.base_url}/api/v1/api-keys/addons/{api_key_id}/recommendations',
            params={'overageRequests': overage_requests}
        )
        return self._handle_response(response)

    def purchase_addon(self, api_key_id: str, package_type: str, 
                      duration_months: int = 1, auto_renew: bool = False, 
                      reason: str = 'Additional capacity needed') -> Dict[str, Any]:
        """Purchase an add-on package"""
        payload = {
            'apiKeyId': api_key_id,
            'addOnPackage': package_type,
            'durationMonths': duration_months,
            'autoRenew': auto_renew,
            'reason': reason
        }
        
        response = self.session.post(f'{self.base_url}/api/v1/api-keys/addons/purchase', json=payload)
        return self._handle_response(response)

# Usage examples
if __name__ == "__main__":
    # Initialize client
    client = MRTFYClient('sk-your-api-key-here')
    
    try:
        # Forward a request
        result = client.forward_request('https://jsonplaceholder.typicode.com/users/1')
        print(f"Forwarded response: {result['body']['name']}")
        
        # Get users
        users = client.get_users(page=1, limit=5)
        print(f"Retrieved {len(users['data'])} users")
        
        # Submit analytics data
        events = [
            {
                'name': 'page_view',
                'timestamp': datetime.now().isoformat(),
                'properties': {'page': '/dashboard', 'user_id': 'user123'}
            }
        ]
        submission = client.submit_data('analytics', events)
        print(f"Data submitted: {submission['id']}")
        
        # Check usage stats
        stats = client.get_usage_stats('your-api-key-id')
        print(f"Usage: {stats['currentUsage']['usagePercentage']:.1f}%")
        
        # Get recommendations if usage is high
        if stats['currentUsage']['usagePercentage'] > 80:
            recommendations = client.get_addon_recommendations('your-api-key-id', 50)
            print(f"Recommended package: {recommendations['recommendedPackage']}")
            
    except requests.exceptions.RequestException as e:
        print(f"API Error: {e}")
```

### **PHP**

```php
<?php

class MRTFYClient {
    private $apiKey;
    private $baseUrl;
    
    public function __construct($apiKey = null, $baseUrl = 'https://api.mrtfy.com/v1') {
        $this->apiKey = $apiKey ?: $_ENV['MRTFY_API_KEY'];
        $this->baseUrl = $baseUrl;
    }
    
    private function logRateLimitInfo($headers) {
        $limit = $headers['X-RateLimit-Limit'] ?? null;
        $remaining = $headers['X-RateLimit-Remaining'] ?? null;
        $tier = $headers['X-RateLimit-Tier'] ?? null;
        $usedAddOn = $headers['X-RateLimit-Used-AddOn'] ?? null;
        
        if ($limit) {
            echo "Rate Limit: {$remaining}/{$limit} ({$tier})\n";
            if ($usedAddOn === 'true') {
                echo "✅ Add-on request used\n";
            }
        }
    }
    
    private function makeRequest($endpoint, $method = 'GET', $data = null) {
        $curl = curl_init();
        
        $headers = [
            'X-API-Key: ' . $this->apiKey,
            'Content-Type: application/json'
        ];
        
        curl_setopt_array($curl, [
            CURLOPT_URL => $this->baseUrl . $endpoint,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CUSTOMREQUEST => $method,
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_HEADERFUNCTION => function($curl, $header) {
                // Parse headers for rate limit info
                if (strpos($header, 'X-RateLimit-') === 0) {
                    $parts = explode(':', $header, 2);
                    if (count($parts) === 2) {
                        $this->responseHeaders[trim($parts[0])] = trim($parts[1]);
                    }
                }
                return strlen($header);
            }
        ]);
        
        if ($data) {
            curl_setopt($curl, CURLOPT_POSTFIELDS, json_encode($data));
        }
        
        $response = curl_exec($curl);
        $statusCode = curl_getinfo($curl, CURLINFO_HTTP_CODE);
        curl_close($curl);
        
        $this->logRateLimitInfo($this->responseHeaders ?? []);
        
        if ($statusCode === 429) {
            $responseData = json_decode($response, true);
            echo "Rate limit exceeded. Recommendations: " . 
                 json_encode($responseData['details']['recommendations']) . "\n";
        }
        
        if ($statusCode >= 400) {
            throw new Exception("API Error: HTTP {$statusCode} - {$response}");
        }
        
        return json_decode($response, true);
    }
    
    public function forwardRequest($url, $method = 'GET', $data = null, $headers = null) {
        $payload = [
            'url' => $url,
            'method' => $method,
            'headers' => $headers ?: ['Accept' => 'application/json']
        ];
        
        if ($data) {
            $payload['body'] = $data;
        }
        
        return $this->makeRequest('/forward', 'POST', $payload);
    }
    
    public function getUsers($page = 1, $limit = 20, $search = '') {
        $query = http_build_query([
            'page' => $page,
            'limit' => $limit,
            'search' => $search
        ]);
        
        return $this->makeRequest("/api/v1/users?{$query}");
    }
    
    public function createUser($userData) {
        return $this->makeRequest('/api/v1/users', 'POST', $userData);
    }
    
    public function submitData($type, $events, $options = null) {
        $payload = [
            'type' => $type,
            'data' => ['events' => $events],
            'options' => $options ?: ['async' => true, 'notify' => true]
        ];
        
        return $this->makeRequest('/api/v1/data/fetch', 'POST', $payload);
    }
    
    public function getUsageStats($apiKeyId, $hours = 24) {
        return $this->makeRequest("/api/v1/api-keys/statistics/{$apiKeyId}?hours={$hours}");
    }
    
    public function getAddonRecommendations($apiKeyId, $overageRequests = 0) {
        return $this->makeRequest("/api/v1/api-keys/addons/{$apiKeyId}/recommendations?overageRequests={$overageRequests}");
    }
    
    public function purchaseAddon($apiKeyId, $packageType, $durationMonths = 1, $autoRenew = false, $reason = 'Additional capacity needed') {
        $payload = [
            'apiKeyId' => $apiKeyId,
            'addOnPackage' => $packageType,
            'durationMonths' => $durationMonths,
            'autoRenew' => $autoRenew,
            'reason' => $reason
        ];
        
        return $this->makeRequest('/api/v1/api-keys/addons/purchase', 'POST', $payload);
    }
}

// Usage
try {
    $client = new MRTFYClient('sk-your-api-key-here');
    
    // Forward a request
    $result = $client->forwardRequest('https://jsonplaceholder.typicode.com/users/1');
    echo "Forwarded response: " . $result['body']['name'] . "\n";
    
    // Get users
    $users = $client->getUsers(1, 5);
    echo "Retrieved " . count($users['data']) . " users\n";
    
    // Submit data
    $events = [
        [
            'name' => 'page_view',
            'timestamp' => date('c'),
            'properties' => ['page' => '/dashboard', 'user_id' => 'user123']
        ]
    ];
    $submission = $client->submitData('analytics', $events);
    echo "Data submitted: " . $submission['id'] . "\n";
    
    // Check usage
    $stats = $client->getUsageStats('your-api-key-id');
    echo "Usage: " . $stats['currentUsage']['usagePercentage'] . "%\n";
    
} catch (Exception $e) {
    echo "Error: " . $e->getMessage() . "\n";
}
?>
```

---

## 🧪 **Testing with Postman**

### **Import the Collection**

1. Download the [MRTFY API Postman Collection](./postman/MRTFY_API_Collection.json)
2. Import into Postman
3. Set up environment variables:
   - `baseUrl`: `http://localhost:8080` (or your API URL)
   - `bearerToken`: (will be set automatically after login)
   - `apiKey`: (will be set automatically after API key creation)
   - `apiKeyId`: (will be set automatically)

### **Testing Workflow**

1. **Authentication Flow**
   - Run "User Registration" 
   - Run "User Login" (saves JWT token automatically)
   - Run "Create API Key" (saves API key automatically)

2. **API Key Testing**
   - Run "Get Users List" to test API key authentication
   - Run "Forward GET Request" to test the gateway
   - Run "Get API Key Usage Statistics" to monitor usage

3. **Rate Limiting Testing**
   - Run "Test Rate Limiting Flow" multiple times (100+) to trigger rate limits
   - Observe rate limit headers in responses
   - See 429 error with add-on recommendations

4. **Add-on Testing**
   - Run "Get Available Add-on Packages"
   - Run "Purchase Add-on Package" 
   - Run "Test Add-on Usage" to see add-on requests being used

### **Automated Testing Scripts**

The Postman collection includes automated scripts that:
- Extract and save tokens/keys automatically
- Log rate limit information
- Handle authentication failures gracefully
- Provide helpful error messages

---

## 🚨 **Error Handling**

### **HTTP Status Codes**

| Code | Status | Description | Action |
|------|--------|-------------|--------|
| **200** | OK | Success | Continue |
| **201** | Created | Resource created | Continue |
| **400** | Bad Request | Invalid parameters | Check request format |
| **401** | Unauthorized | Invalid authentication | Check API key/JWT |
| **403** | Forbidden | Insufficient permissions | Check user roles |
| **404** | Not Found | Resource not found | Check resource ID |
| **429** | Too Many Requests | Rate limit exceeded | Purchase add-ons or wait |
| **500** | Internal Server Error | Server error | Contact support |

### **Error Response Format**

```json
{
  "error": "Rate limit exceeded",
  "status": 429,
  "timestamp": "2024-01-15T14:30:00Z",
  "details": {
    "currentTier": "BASIC",
    "dailyLimit": 100,
    "requestsUsed": 100,
    "resetIn": "14 hours",
    "recommendations": [
      {
        "package": "ADDON_SMALL",
        "cost": "$5/month",
        "additionalRequests": 100,
        "description": "Perfect for your current usage pattern"
      }
    ]
  }
}
```

### **Error Handling Best Practices**

```javascript
// JavaScript example
async function handleApiCall(apiCall) {
  try {
    const response = await apiCall();
    return response.data;
  } catch (error) {
    if (error.response) {
      const { status, data } = error.response;
      
      switch (status) {
        case 401:
          console.error('Authentication failed:', data.details?.message);
          // Redirect to login or refresh token
          break;
          
        case 429:
          console.warn('Rate limit exceeded');
          if (data.details?.recommendations) {
            console.log('Recommended add-ons:', data.details.recommendations);
          }
          // Implement exponential backoff
          await new Promise(resolve => setTimeout(resolve, 60000));
          return handleApiCall(apiCall); // Retry
          
        case 500:
          console.error('Server error:', data.error);
          // Contact support or try again later
          break;
          
        default:
          console.error(`API Error ${status}:`, data.error);
      }
    } else {
      console.error('Network error:', error.message);
    }
    throw error;
  }
}
```

---

## 💡 **Best Practices**

### **1. Security**

```bash
# ✅ Good: Use environment variables
export MRTFY_API_KEY="sk-1234567890abcdef..."
curl -H "X-API-Key: $MRTFY_API_KEY" https://api.mrtfy.com/v1/api/v1/users

# ❌ Bad: Hardcode in code
const apiKey = "sk-1234567890abcdef..."; // Never do this!
```

### **2. Rate Limit Management**

```javascript
// Monitor usage proactively
async function checkUsageAndWarn() {
  const stats = await client.getUsageStats(apiKeyId);
  const usagePercent = stats.currentUsage.usagePercentage;
  
  if (usagePercent > 80) {
    console.warn(`⚠️ High usage: ${usagePercent}% of daily limit used`);
    
    if (usagePercent > 95) {
      // Get recommendations
      const recommendations = await client.getAddonRecommendations(apiKeyId, 50);
      console.log('Consider purchasing:', recommendations.recommendedPackage);
    }
  }
}
```

### **3. Graceful Degradation**

```javascript
// Implement circuit breaker pattern
class CircuitBreaker {
  constructor(threshold = 5, timeout = 60000) {
    this.failureCount = 0;
    this.threshold = threshold;
    this.timeout = timeout;
    this.state = 'CLOSED'; // CLOSED, OPEN, HALF_OPEN
    this.nextAttempt = Date.now();
  }

  async call(fn) {
    if (this.state === 'OPEN') {
      if (Date.now() < this.nextAttempt) {
        throw new Error('Circuit breaker is OPEN');
      }
      this.state = 'HALF_OPEN';
    }

    try {
      const result = await fn();
      this.onSuccess();
      return result;
    } catch (error) {
      this.onFailure();
      throw error;
    }
  }

  onSuccess() {
    this.failureCount = 0;
    this.state = 'CLOSED';
  }

  onFailure() {
    this.failureCount++;
    if (this.failureCount >= this.threshold) {
      this.state = 'OPEN';
      this.nextAttempt = Date.now() + this.timeout;
    }
  }
}
```

### **4. Efficient Batching**

```javascript
// Batch multiple operations
class BatchProcessor {
  constructor(client, batchSize = 10, delay = 1000) {
    this.client = client;
    this.batchSize = batchSize;
    this.delay = delay;
    this.queue = [];
  }

  async add(operation) {
    this.queue.push(operation);
    
    if (this.queue.length >= this.batchSize) {
      await this.processBatch();
    }
  }

  async processBatch() {
    const batch = this.queue.splice(0, this.batchSize);
    
    try {
      const results = await Promise.all(
        batch.map(op => this.client[op.method](...op.args))
      );
      
      // Process results
      batch.forEach((op, index) => {
        if (op.callback) {
          op.callback(null, results[index]);
        }
      });
      
    } catch (error) {
      // Handle batch errors
      batch.forEach(op => {
        if (op.callback) {
          op.callback(error, null);
        }
      });
    }

    // Rate limiting delay
    await new Promise(resolve => setTimeout(resolve, this.delay));
  }
}
```

### **5. Monitoring and Alerting**

```javascript
// Set up monitoring
class ApiMonitor {
  constructor(client, alertThresholds = {}) {
    this.client = client;
    this.thresholds = {
      usageWarning: 80,
      usageCritical: 95,
      errorRate: 5,
      ...alertThresholds
    };
    this.metrics = {
      requests: 0,
      errors: 0,
      lastCheck: Date.now()
    };
  }

  async checkHealth() {
    try {
      const stats = await this.client.getUsageStats(apiKeyId);
      const usage = stats.currentUsage.usagePercentage;
      
      if (usage > this.thresholds.usageCritical) {
        this.alert('CRITICAL', `Usage at ${usage}%`);
      } else if (usage > this.thresholds.usageWarning) {
        this.alert('WARNING', `Usage at ${usage}%`);
      }
      
      const errorRate = (this.metrics.errors / this.metrics.requests) * 100;
      if (errorRate > this.thresholds.errorRate) {
        this.alert('ERROR', `Error rate at ${errorRate}%`);
      }
      
    } catch (error) {
      this.alert('ERROR', `Health check failed: ${error.message}`);
    }
  }

  alert(level, message) {
    console.log(`[${level}] ${new Date().toISOString()}: ${message}`);
    // Send to monitoring service (Slack, email, etc.)
  }
}
```

---

## 🚀 **Deployment Guide**

### **Environment Configuration**

#### **Development**
```yaml
# application-dev.yml
app:
  auth:
    method: both
    detailed-errors: true
    require-auth: false

logging:
  level:
    com.example.jwtauthenticator: DEBUG
```

#### **Staging**
```yaml
# application-staging.yml
app:
  auth:
    method: both
    detailed-errors: true
    require-auth: true

logging:
  level:
    com.example.jwtauthenticator: INFO
```

#### **Production**
```yaml
# application-prod.yml
app:
  auth:
    method: api_key
    detailed-errors: false
    require-auth: true
    fallback: false

logging:
  level:
    com.example.jwtauthenticator: WARN
```

### **Docker Deployment**

```dockerfile
# Dockerfile
FROM openjdk:17-jdk-slim

COPY target/jwt-authenticator-1.0.0.jar app.jar

# Environment variables for dynamic auth
ENV SPRING_PROFILES_ACTIVE=prod
ENV APP_AUTH_METHOD=api_key
ENV APP_AUTH_REQUIRE_AUTH=true

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```yaml
# docker-compose.yml
version: '3.8'
services:
  mrtfy-api:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - APP_AUTH_METHOD=api_key
      - APP_AUTH_DETAILED_ERRORS=false
      - DATABASE_URL=jdbc:postgresql://db:5432/mrtfy
    depends_on:
      - db
      
  db:
    image: postgres:15
    environment:
      - POSTGRES_DB=mrtfy
      - POSTGRES_USER=mrtfy
      - POSTGRES_PASSWORD=secure_password
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

### **Kubernetes Deployment**

```yaml
# k8s-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mrtfy-api
spec:
  replicas: 3
  selector:
    matchLabels:
      app: mrtfy-api
  template:
    metadata:
      labels:
        app: mrtfy-api
    spec:
      containers:
      - name: mrtfy-api
        image: mrtfy/api:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: APP_AUTH_METHOD
          value: "api_key"
        - name: APP_AUTH_REQUIRE_AUTH
          value: "true"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: mrtfy-api-service
spec:
  selector:
    app: mrtfy-api
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

### **Environment Variables Reference**

| Variable | Description | Default | Example |
|----------|-------------|---------|---------|
| `APP_AUTH_METHOD` | Authentication method | `both` | `api_key`, `jwt`, `both` |
| `APP_AUTH_API_KEY_HEADER` | API key header name | `X-API-Key` | `X-API-Key` |
| `APP_AUTH_JWT_HEADER` | JWT header name | `Authorization` | `Authorization` |
| `APP_AUTH_FALLBACK` | Allow auth fallback | `true` | `true`, `false` |
| `APP_AUTH_REQUIRE_AUTH` | Require authentication | `true` | `true`, `false` |
| `APP_AUTH_DETAILED_ERRORS` | Show detailed errors | `true` | `true`, `false` |

### **Health Checks**

```bash
# Health check endpoint
curl http://localhost:8080/actuator/health

# Custom auth health check
curl -H "X-API-Key: sk-test-key" http://localhost:8080/api/v1/users?limit=1
```

### **Monitoring Setup**

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'mrtfy-api'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
```

---

## 📞 **Support & Resources**

### **Documentation**
- 📚 **API Reference**: [Swagger UI](http://localhost:8080/swagger-ui.html)
- 📖 **User Guide**: [User Documentation](./USER_API_DOCUMENTATION.md)
- 🔧 **Postman Collection**: [Download](./postman/MRTFY_API_Collection.json)

### **Support Channels**
- 📧 **Email**: api-support@mrtfy.com
- 💬 **Community**: https://community.mrtfy.com
- 🐛 **Issues**: https://github.com/mrtfy/api-issues
- 📞 **Enterprise Support**: +1-800-MRTFY-API

### **Resources**
- 🎥 **Video Tutorials**: https://youtube.com/mrtfy-api
- 📝 **Blog**: https://blog.mrtfy.com/api
- 🔗 **Status Page**: https://status.mrtfy.com
- 📊 **Changelog**: https://changelog.mrtfy.com

---

**🎉 Congratulations!** You now have everything you need to integrate with the MRTFY API. The dynamic authentication system provides unparalleled flexibility, while the comprehensive add-on system ensures you never hit hard limits.

**Happy coding! 🚀**

*The MRTFY API Team*
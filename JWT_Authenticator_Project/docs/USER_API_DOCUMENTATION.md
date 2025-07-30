# MRTFY API - User Documentation

Welcome to the MRTFY API! This guide will help you get started with generating API keys and using our professional API gateway service.

## 🚀 **Quick Start Guide**

### **Step 1: Create Your Account & Get API Key**

#### **1.1 Register & Login**
```bash
# Register a new account
curl -X POST https://api.mrtfy.com/v1/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your_username",
    "email": "your@email.com", 
    "password": "SecurePassword123!",
    "firstName": "Your",
    "lastName": "Name"
  }'

# Login to get JWT token
curl -X POST https://api.mrtfy.com/v1/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your_username",
    "password": "SecurePassword123!"
  }'

# Response includes your JWT token
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "expiresIn": 3600
}
```

#### **1.2 Generate Your API Key**
```bash
# Create an API key using your JWT token
curl -X POST https://api.mrtfy.com/v1/api/v1/api-keys \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Application API Key",
    "rateLimitTier": "BASIC",
    "description": "API key for my awesome application"
  }'

# Response (SAVE THE keyValue - it's shown only once!)
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "My Application API Key", 
  "keyValue": "sk-1234567890abcdef1234567890abcdef12345678",
  "rateLimitTier": "BASIC",
  "description": "API key for my awesome application",
  "isActive": true,
  "expiresAt": "2025-01-15T10:30:00Z",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

> **⚠️ IMPORTANT**: Save your API key securely! It's only shown once and cannot be retrieved again.

### **Step 2: Start Making API Calls**

Once you have your API key, you can access all available endpoints:

## 📋 **Available Endpoints After API Key Creation**

### **🌐 Main Gateway Endpoint**

#### **Forward Requests to External APIs**
```bash
# Forward a GET request
curl -X POST https://api.mrtfy.com/v1/forward \
  -H "X-API-Key: sk-1234567890abcdef..." \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://api.example.com/users",
    "method": "GET",
    "headers": {
      "Accept": "application/json"
    }
  }'

# Forward a POST request with data
curl -X POST https://api.mrtfy.com/v1/forward \
  -H "X-API-Key: sk-1234567890abcdef..." \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://api.example.com/users",
    "method": "POST",
    "headers": {
      "Content-Type": "application/json"
    },
    "body": {
      "name": "John Doe",
      "email": "john@example.com"
    }
  }'
```

### **👥 Sample Protected Endpoints**

#### **Users Management**
```bash
# Get users list
curl -X GET "https://api.mrtfy.com/v1/api/v1/users?page=1&limit=20" \
  -H "X-API-Key: sk-1234567890abcdef..."

# Create a new user
curl -X POST https://api.mrtfy.com/v1/api/v1/users \
  -H "X-API-Key: sk-1234567890abcdef..." \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com",
    "role": "user"
  }'
```

#### **Data Operations**
```bash
# Fetch data with filters
curl -X GET "https://api.mrtfy.com/v1/api/v1/data/fetch?type=analytics&startDate=2024-01-01&endDate=2024-01-31" \
  -H "X-API-Key: sk-1234567890abcdef..."

# Submit data for processing
curl -X POST https://api.mrtfy.com/v1/api/v1/data/fetch \
  -H "X-API-Key: sk-1234567890abcdef..." \
  -H "Content-Type: application/json" \
  -d '{
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
  }'
```

### **📊 Usage Monitoring**
```bash
# Check your API usage statistics
curl -X GET https://api.mrtfy.com/v1/api/v1/api-keys/statistics/YOUR_API_KEY_ID \
  -H "X-API-Key: sk-1234567890abcdef..."

# Response shows your usage
{
  "apiKeyId": "550e8400-e29b-41d4-a716-446655440000",
  "currentUsage": {
    "requestCount": 45,
    "requestLimit": 100,
    "remainingRequests": 55,
    "usagePercentage": 45.0,
    "windowStart": "2024-01-15T00:00:00Z",
    "windowEnd": "2024-01-15T23:59:59Z"
  },
  "totalAvailable": 55,
  "isCurrentlyRateLimited": false
}
```

## 🔐 **Authentication Methods**

### **Method 1: X-API-Key Header (Recommended)**
```bash
curl -X GET https://api.mrtfy.com/v1/api/v1/users \
  -H "X-API-Key: sk-1234567890abcdef..."
```

### **Method 2: Authorization Header**
```bash
curl -X GET https://api.mrtfy.com/v1/api/v1/users \
  -H "Authorization: sk-1234567890abcdef..."
```

## 📈 **Rate Limits & Pricing**

### **Base Tiers**
| Tier | Daily Limit | Price | Best For |
|------|-------------|-------|----------|
| **BASIC** | 100 requests/day | **FREE** | Testing & small projects |
| **STANDARD** | 500 requests/day | $10/month | Small applications |
| **PREMIUM** | 2000 requests/day | $50/month | Growing businesses |
| **ENTERPRISE** | 10000 requests/day | $200/month | Large applications |
| **UNLIMITED** | No limits | $500/month | Enterprise solutions |

### **Add-on Packages (When You Need More)**
| Package | Additional Requests | Price | Perfect For |
|---------|-------------------|-------|-------------|
| **Small** | +100/day | $5/month | Occasional spikes |
| **Medium** | +500/day | $20/month | Regular overages |
| **Large** | +2000/day | $75/month | High-volume periods |
| **Enterprise** | +10000/day | $300/month | Massive scaling |

### **Understanding Rate Limit Headers**
Every response includes helpful headers:

```http
X-RateLimit-Limit: 100                    # Your daily limit
X-RateLimit-Remaining: 45                 # Requests left today
X-RateLimit-Tier: BASIC                   # Your current tier
X-RateLimit-Additional-Available: 0       # Add-on requests available
X-RateLimit-Total-Remaining: 45           # Total requests remaining
X-RateLimit-Reset: 43200                  # Seconds until reset (12 hours)
X-RateLimit-Used-AddOn: false             # Whether add-on was used
```

## 🛒 **Scaling Your Usage**

### **When You Hit Your Limits**
When you exceed your daily limit, you'll get a helpful response:

```json
{
  "error": "Rate limit exceeded. Consider purchasing add-on requests.",
  "status": 429,
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

### **Purchase Add-on Packages**
```bash
# Get personalized recommendations
curl -X GET "https://api.mrtfy.com/v1/api/v1/api-keys/addons/YOUR_API_KEY_ID/recommendations?overageRequests=50" \
  -H "X-API-Key: sk-1234567890abcdef..."

# Purchase an add-on package
curl -X POST https://api.mrtfy.com/v1/api/v1/api-keys/addons/purchase \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "apiKeyId": "YOUR_API_KEY_ID",
    "addOnPackage": "ADDON_SMALL",
    "durationMonths": 1,
    "autoRenew": true,
    "reason": "Need more requests for product launch"
  }'
```

### **Automatic Add-on Usage**
Once you purchase add-ons, they're used automatically when your base limit is exceeded:

```http
# Request that uses add-on (base limit exceeded)
X-RateLimit-Limit: 100                    # Base limit
X-RateLimit-Remaining: 0                  # Base exhausted
X-RateLimit-Additional-Available: 99      # Add-on requests left
X-RateLimit-Total-Remaining: 99           # Total available
X-RateLimit-Used-AddOn: true              # Add-on was used ✅
```

## 🔧 **Request & Response Formats**

### **Standard Request Headers**
```http
X-API-Key: sk-1234567890abcdef...          # Your API key
Content-Type: application/json             # For POST/PUT requests
Accept: application/json                   # Expected response format
```

### **Query Parameters**
Most endpoints support common query parameters:

```bash
# Pagination
?page=1&limit=20

# Filtering  
?search=john&active=true

# Date ranges
?startDate=2024-01-01&endDate=2024-01-31

# Sorting
?sort=createdAt&order=desc
```

### **Request Body Formats**

#### **JSON (Most Common)**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "metadata": {
    "source": "api",
    "campaign": "signup_flow"
  }
}
```

#### **Forward Request Format**
```json
{
  "url": "https://api.target.com/endpoint",
  "method": "POST",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer target_api_token"
  },
  "body": {
    "data": "to_forward"
  },
  "timeout": 30
}
```

### **Response Formats**

#### **Success Response**
```json
{
  "id": "resource_id",
  "name": "Resource Name",
  "status": "active",
  "createdAt": "2024-01-15T10:30:00Z",
  "metadata": {
    "version": "1.0"
  }
}
```

#### **List Response**
```json
{
  "data": [
    {
      "id": "item1",
      "name": "Item 1"
    },
    {
      "id": "item2", 
      "name": "Item 2"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 150,
    "totalPages": 8
  }
}
```

#### **Error Response**
```json
{
  "error": "Validation failed",
  "status": 400,
  "timestamp": "2024-01-15T14:30:00Z",
  "details": {
    "field": "email",
    "message": "Invalid email format"
  }
}
```

## 🚨 **Error Handling**

### **Common HTTP Status Codes**
- **200** - Success
- **201** - Created successfully
- **400** - Bad request (check your parameters)
- **401** - Invalid API key
- **403** - Insufficient permissions
- **404** - Resource not found
- **429** - Rate limit exceeded
- **500** - Server error (contact support)

### **Rate Limit Exceeded (429)**
```json
{
  "error": "Rate limit exceeded",
  "status": 429,
  "details": {
    "resetIn": "14 hours",
    "recommendations": [
      {
        "package": "ADDON_SMALL",
        "cost": "$5/month"
      }
    ]
  }
}
```

### **Invalid API Key (401)**
```json
{
  "error": "Invalid API key",
  "status": 401,
  "details": {
    "message": "The provided API key is invalid or has been revoked"
  }
}
```

## 💡 **Best Practices**

### **1. Secure Your API Key**
```bash
# ✅ Good: Use environment variables
export MRTFY_API_KEY="sk-1234567890abcdef..."
curl -H "X-API-Key: $MRTFY_API_KEY" https://api.mrtfy.com/v1/api/v1/users

# ❌ Bad: Hardcode in your application
const apiKey = "sk-1234567890abcdef..."; // Don't do this!
```

### **2. Handle Rate Limits Gracefully**
```javascript
// Example in JavaScript
async function makeApiCall(url, options) {
  try {
    const response = await fetch(url, {
      ...options,
      headers: {
        'X-API-Key': process.env.MRTFY_API_KEY,
        'Content-Type': 'application/json',
        ...options.headers
      }
    });
    
    if (response.status === 429) {
      const rateLimitData = await response.json();
      console.log('Rate limit exceeded. Consider upgrading:', rateLimitData.details.recommendations);
      
      // Implement exponential backoff
      const retryAfter = response.headers.get('Retry-After') || 60;
      await new Promise(resolve => setTimeout(resolve, retryAfter * 1000));
      
      // Retry the request
      return makeApiCall(url, options);
    }
    
    return response.json();
  } catch (error) {
    console.error('API call failed:', error);
    throw error;
  }
}
```

### **3. Monitor Your Usage**
```bash
# Check usage regularly
curl -X GET https://api.mrtfy.com/v1/api/v1/api-keys/statistics/YOUR_API_KEY_ID \
  -H "X-API-Key: sk-1234567890abcdef..."

# Set up alerts when you reach 80% of your limit
if [ "$usage_percentage" -gt 80 ]; then
  echo "Warning: API usage at ${usage_percentage}%"
fi
```

### **4. Use Appropriate HTTP Methods**
```bash
# ✅ Correct HTTP methods
GET    /users           # Retrieve data
POST   /users           # Create new resource  
PUT    /users/123       # Update entire resource
PATCH  /users/123       # Partial update
DELETE /users/123       # Delete resource
```

## 🔄 **Dynamic API Key Access Implementation**

### **Configurable Authentication Middleware**

Instead of hardcoding authentication methods, implement a dynamic approach:

#### **Environment Configuration**
```bash
# .env file
AUTH_METHOD=api_key              # or 'jwt' or 'both'
API_KEY_HEADER=X-API-Key         # Configurable header name
JWT_HEADER=Authorization         # JWT header name
FALLBACK_AUTH=true               # Allow fallback between methods
```

#### **Dynamic Authentication Strategy**
```javascript
// Example middleware implementation
class AuthenticationStrategy {
  constructor(config) {
    this.authMethod = config.AUTH_METHOD || 'both';
    this.apiKeyHeader = config.API_KEY_HEADER || 'X-API-Key';
    this.jwtHeader = config.JWT_HEADER || 'Authorization';
    this.allowFallback = config.FALLBACK_AUTH === 'true';
  }

  async authenticate(request) {
    const strategies = this.getEnabledStrategies();
    
    for (const strategy of strategies) {
      try {
        const result = await this.executeStrategy(strategy, request);
        if (result.success) {
          return result;
        }
      } catch (error) {
        if (!this.allowFallback) {
          throw error;
        }
        // Continue to next strategy
      }
    }
    
    throw new Error('Authentication failed');
  }

  getEnabledStrategies() {
    switch (this.authMethod) {
      case 'api_key': return ['apiKey'];
      case 'jwt': return ['jwt'];
      case 'both': return ['apiKey', 'jwt'];
      default: return ['apiKey', 'jwt'];
    }
  }

  async executeStrategy(strategy, request) {
    switch (strategy) {
      case 'apiKey':
        return this.validateApiKey(request.headers[this.apiKeyHeader]);
      case 'jwt':
        return this.validateJWT(request.headers[this.jwtHeader]);
      default:
        throw new Error(`Unknown strategy: ${strategy}`);
    }
  }
}
```

### **Benefits of Dynamic Approach**
- ✅ **No Code Changes**: Switch auth methods via configuration
- ✅ **Gradual Migration**: Support both methods during transition
- ✅ **Environment Specific**: Different auth for dev/staging/prod
- ✅ **Future Proof**: Easy to add new auth methods
- ✅ **Reduced Maintenance**: Single codebase for multiple auth strategies

## 📚 **Code Examples in Multiple Languages**

### **JavaScript/Node.js**
```javascript
const axios = require('axios');

const mrtfyApi = axios.create({
  baseURL: 'https://api.mrtfy.com/v1',
  headers: {
    'X-API-Key': process.env.MRTFY_API_KEY,
    'Content-Type': 'application/json'
  }
});

// Forward a request
async function forwardRequest(targetUrl, method = 'GET', data = null) {
  try {
    const response = await mrtfyApi.post('/forward', {
      url: targetUrl,
      method: method,
      headers: { 'Accept': 'application/json' },
      ...(data && { body: data })
    });
    
    return response.data;
  } catch (error) {
    if (error.response?.status === 429) {
      console.log('Rate limit exceeded. Recommendations:', 
                  error.response.data.details.recommendations);
    }
    throw error;
  }
}

// Get users
async function getUsers(page = 1, limit = 20) {
  const response = await mrtfyApi.get(`/api/v1/users?page=${page}&limit=${limit}`);
  return response.data;
}
```

### **Python**
```python
import requests
import os
from typing import Optional, Dict, Any

class MRTFYClient:
    def __init__(self, api_key: str = None):
        self.api_key = api_key or os.getenv('MRTFY_API_KEY')
        self.base_url = 'https://api.mrtfy.com/v1'
        self.session = requests.Session()
        self.session.headers.update({
            'X-API-Key': self.api_key,
            'Content-Type': 'application/json'
        })
    
    def forward_request(self, url: str, method: str = 'GET', 
                       data: Optional[Dict] = None) -> Dict[str, Any]:
        """Forward a request through MRTFY gateway"""
        payload = {
            'url': url,
            'method': method,
            'headers': {'Accept': 'application/json'}
        }
        
        if data:
            payload['body'] = data
            
        response = self.session.post(f'{self.base_url}/forward', json=payload)
        
        if response.status_code == 429:
            rate_limit_info = response.json()
            print(f"Rate limit exceeded. Recommendations: {rate_limit_info['details']['recommendations']}")
            
        response.raise_for_status()
        return response.json()
    
    def get_users(self, page: int = 1, limit: int = 20) -> Dict[str, Any]:
        """Get users list"""
        response = self.session.get(
            f'{self.base_url}/api/v1/users',
            params={'page': page, 'limit': limit}
        )
        response.raise_for_status()
        return response.json()
    
    def get_usage_stats(self, api_key_id: str) -> Dict[str, Any]:
        """Get API usage statistics"""
        response = self.session.get(
            f'{self.base_url}/api/v1/api-keys/statistics/{api_key_id}'
        )
        response.raise_for_status()
        return response.json()

# Usage
client = MRTFYClient()
users = client.get_users(page=1, limit=10)
print(f"Retrieved {len(users['data'])} users")
```

### **PHP**
```php
<?php

class MRTFYClient {
    private $apiKey;
    private $baseUrl = 'https://api.mrtfy.com/v1';
    
    public function __construct($apiKey = null) {
        $this->apiKey = $apiKey ?: $_ENV['MRTFY_API_KEY'];
    }
    
    public function forwardRequest($url, $method = 'GET', $data = null) {
        $payload = [
            'url' => $url,
            'method' => $method,
            'headers' => ['Accept' => 'application/json']
        ];
        
        if ($data) {
            $payload['body'] = $data;
        }
        
        $response = $this->makeRequest('/forward', 'POST', $payload);
        
        if ($response['status_code'] === 429) {
            echo "Rate limit exceeded. Recommendations: " . 
                 json_encode($response['data']['details']['recommendations']);
        }
        
        return $response['data'];
    }
    
    public function getUsers($page = 1, $limit = 20) {
        $response = $this->makeRequest("/api/v1/users?page={$page}&limit={$limit}");
        return $response['data'];
    }
    
    private function makeRequest($endpoint, $method = 'GET', $data = null) {
        $curl = curl_init();
        
        curl_setopt_array($curl, [
            CURLOPT_URL => $this->baseUrl . $endpoint,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CUSTOMREQUEST => $method,
            CURLOPT_HTTPHEADER => [
                'X-API-Key: ' . $this->apiKey,
                'Content-Type: application/json'
            ]
        ]);
        
        if ($data) {
            curl_setopt($curl, CURLOPT_POSTFIELDS, json_encode($data));
        }
        
        $response = curl_exec($curl);
        $statusCode = curl_getinfo($curl, CURLINFO_HTTP_CODE);
        curl_close($curl);
        
        return [
            'status_code' => $statusCode,
            'data' => json_decode($response, true)
        ];
    }
}

// Usage
$client = new MRTFYClient();
$users = $client->getUsers(1, 10);
echo "Retrieved " . count($users['data']) . " users\n";
?>
```

### **cURL Examples**
```bash
#!/bin/bash

# Set your API key
API_KEY="sk-1234567890abcdef..."
BASE_URL="https://api.mrtfy.com/v1"

# Function to make API calls with rate limit handling
make_api_call() {
    local endpoint=$1
    local method=${2:-GET}
    local data=${3:-}
    
    if [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X $method \
            -H "X-API-Key: $API_KEY" \
            -H "Content-Type: application/json" \
            -d "$data" \
            "$BASE_URL$endpoint")
    else
        response=$(curl -s -w "\n%{http_code}" -X $method \
            -H "X-API-Key: $API_KEY" \
            "$BASE_URL$endpoint")
    fi
    
    # Extract body and status code
    body=$(echo "$response" | head -n -1)
    status_code=$(echo "$response" | tail -n 1)
    
    if [ "$status_code" = "429" ]; then
        echo "Rate limit exceeded. Response: $body"
        return 1
    elif [ "$status_code" -ge "400" ]; then
        echo "Error $status_code: $body"
        return 1
    else
        echo "$body"
        return 0
    fi
}

# Examples
echo "Getting users..."
make_api_call "/api/v1/users?page=1&limit=5"

echo -e "\nForwarding request..."
make_api_call "/forward" "POST" '{
    "url": "https://jsonplaceholder.typicode.com/users/1",
    "method": "GET",
    "headers": {"Accept": "application/json"}
}'

echo -e "\nChecking usage stats..."
make_api_call "/api/v1/api-keys/statistics/YOUR_API_KEY_ID"
```

## 🎯 **Next Steps**

1. **Create Your API Key**: Follow the Quick Start guide above
2. **Test the Endpoints**: Use our Postman collection or cURL examples
3. **Monitor Your Usage**: Keep track of your API consumption
4. **Scale When Needed**: Purchase add-ons or upgrade your tier
5. **Integrate**: Use our code examples to integrate with your application

## 📞 **Support & Resources**

- **📧 Email**: api-support@mrtfy.com
- **📚 Documentation**: https://docs.mrtfy.com
- **🐛 Issues**: https://github.com/mrtfy/api-issues
- **💬 Community**: https://community.mrtfy.com

---

**Happy coding! 🚀**

*The MRTFY API Team*
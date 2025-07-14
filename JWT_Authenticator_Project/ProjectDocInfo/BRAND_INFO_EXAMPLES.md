# Brand Info API - Usage Examples

## Quick Start

The Brand Info API is now available at `/auth/brand-info` and ready to use. Here are practical examples:

## cURL Examples

### Test Direct URL Resolution
```bash
curl "http://localhost:8080/myapp/auth/brand-info?query=https://www.google.com"
```

**Expected Response:**
```json
{
  "status": "success",
  "resolvedUrl": "https://www.google.com"
}
```

### Test Domain Name Resolution
```bash
curl "http://localhost:8080/myapp/auth/brand-info?query=microsoft.com"
```

**Expected Response:**
```json
{
  "status": "success",
  "resolvedUrl": "https://www.microsoft.com"
}
```

### Test Company Name Search
```bash
curl "http://localhost:8080/myapp/auth/brand-info?query=Apple"
```

**Expected Response (requires Google API configuration):**
```json
{
  "status": "success",
  "resolvedUrl": "https://www.apple.com"
}
```

### Test Error Handling
```bash
curl "http://localhost:8080/myapp/auth/brand-info?query=nonexistentdomain12345.com"
```

**Expected Response:**
```json
{
  "status": "error",
  "message": "The provided domain name does not have an associated active website."
}
```

## JavaScript/Frontend Examples

### Basic Usage
```javascript
async function getBrandInfo(query) {
    try {
        const response = await fetch(`/myapp/auth/brand-info?query=${encodeURIComponent(query)}`);
        const data = await response.json();
        
        if (data.status === 'success') {
            console.log('✅ Website found:', data.resolvedUrl);
            return data.resolvedUrl;
        } else {
            console.log('❌ Error:', data.message);
            return null;
        }
    } catch (error) {
        console.error('🚨 Request failed:', error);
        return null;
    }
}

// Test different types of inputs
getBrandInfo('https://www.google.com');  // Direct URL
getBrandInfo('microsoft.com');           // Domain name
getBrandInfo('Apple');                   // Company name
```

### With Error Handling and Loading States
```javascript
class BrandInfoClient {
    constructor(baseUrl = '/myapp') {
        this.baseUrl = baseUrl;
    }

    async resolveBrand(query) {
        if (!query || query.trim() === '') {
            throw new Error('Query cannot be empty');
        }

        const url = `${this.baseUrl}/auth/brand-info?query=${encodeURIComponent(query.trim())}`;
        
        try {
            const response = await fetch(url);
            
            if (response.status === 429) {
                const errorData = await response.json();
                throw new Error(`Rate limit exceeded. Retry after ${errorData.retryAfter} seconds.`);
            }
            
            const data = await response.json();
            
            if (data.status === 'success') {
                return {
                    success: true,
                    url: data.resolvedUrl,
                    message: 'Website found successfully'
                };
            } else {
                return {
                    success: false,
                    url: null,
                    message: data.message
                };
            }
        } catch (error) {
            return {
                success: false,
                url: null,
                message: `Request failed: ${error.message}`
            };
        }
    }
}

// Usage
const brandClient = new BrandInfoClient();

// Example with loading states
async function handleBrandSearch(query) {
    console.log('🔍 Searching for:', query);
    
    const result = await brandClient.resolveBrand(query);
    
    if (result.success) {
        console.log('✅ Success:', result.url);
        // Update UI with successful result
        document.getElementById('result').innerHTML = `
            <div class="success">
                <h3>Website Found!</h3>
                <a href="${result.url}" target="_blank">${result.url}</a>
            </div>
        `;
    } else {
        console.log('❌ Failed:', result.message);
        // Update UI with error
        document.getElementById('result').innerHTML = `
            <div class="error">
                <h3>Not Found</h3>
                <p>${result.message}</p>
            </div>
        `;
    }
}
```

## React Component Example

```jsx
import React, { useState } from 'react';

const BrandInfoSearch = () => {
    const [query, setQuery] = useState('');
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    const searchBrand = async () => {
        if (!query.trim()) {
            setError('Please enter a URL, domain, or company name');
            return;
        }

        setLoading(true);
        setError(null);
        setResult(null);

        try {
            const response = await fetch(`/myapp/auth/brand-info?query=${encodeURIComponent(query)}`);
            const data = await response.json();

            if (data.status === 'success') {
                setResult(data.resolvedUrl);
            } else {
                setError(data.message);
            }
        } catch (err) {
            setError('Network error occurred');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="brand-info-search">
            <h2>Brand Info Search</h2>
            
            <div className="search-form">
                <input
                    type="text"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Enter URL, domain, or company name..."
                    onKeyPress={(e) => e.key === 'Enter' && searchBrand()}
                />
                <button onClick={searchBrand} disabled={loading}>
                    {loading ? 'Searching...' : 'Search'}
                </button>
            </div>

            {result && (
                <div className="result success">
                    <h3>✅ Website Found!</h3>
                    <a href={result} target="_blank" rel="noopener noreferrer">
                        {result}
                    </a>
                </div>
            )}

            {error && (
                <div className="result error">
                    <h3>❌ Error</h3>
                    <p>{error}</p>
                </div>
            )}
        </div>
    );
};

export default BrandInfoSearch;
```

## Python Example

```python
import requests
import json
from urllib.parse import urlencode

class BrandInfoClient:
    def __init__(self, base_url="http://localhost:8080/myapp"):
        self.base_url = base_url

    def resolve_brand(self, query):
        """
        Resolve brand information from query
        
        Args:
            query (str): URL, domain name, or company name
            
        Returns:
            dict: Response with status and result
        """
        if not query or not query.strip():
            return {"success": False, "error": "Query cannot be empty"}

        url = f"{self.base_url}/auth/brand-info"
        params = {"query": query.strip()}

        try:
            response = requests.get(url, params=params, timeout=30)
            
            if response.status_code == 429:
                return {
                    "success": False, 
                    "error": "Rate limit exceeded",
                    "retry_after": response.json().get("retryAfter", 60)
                }

            data = response.json()

            if data["status"] == "success":
                return {
                    "success": True,
                    "url": data["resolvedUrl"],
                    "message": "Website found successfully"
                }
            else:
                return {
                    "success": False,
                    "error": data["message"]
                }

        except requests.RequestException as e:
            return {
                "success": False,
                "error": f"Request failed: {str(e)}"
            }

# Usage examples
client = BrandInfoClient()

# Test different inputs
test_queries = [
    "https://www.google.com",
    "microsoft.com", 
    "Apple",
    "nonexistent-domain-123.com"
]

for query in test_queries:
    print(f"\n🔍 Testing: {query}")
    result = client.resolve_brand(query)
    
    if result["success"]:
        print(f"✅ Success: {result['url']}")
    else:
        print(f"❌ Error: {result['error']}")
```

## PHP Example

```php
<?php

class BrandInfoClient {
    private $baseUrl;

    public function __construct($baseUrl = 'http://localhost:8080/myapp') {
        $this->baseUrl = $baseUrl;
    }

    public function resolveBrand($query) {
        if (empty(trim($query))) {
            return [
                'success' => false,
                'error' => 'Query cannot be empty'
            ];
        }

        $url = $this->baseUrl . '/auth/brand-info?' . http_build_query(['query' => trim($query)]);

        $context = stream_context_create([
            'http' => [
                'timeout' => 30,
                'method' => 'GET'
            ]
        ]);

        $response = @file_get_contents($url, false, $context);
        
        if ($response === false) {
            return [
                'success' => false,
                'error' => 'Request failed'
            ];
        }

        $data = json_decode($response, true);

        if ($data['status'] === 'success') {
            return [
                'success' => true,
                'url' => $data['resolvedUrl'],
                'message' => 'Website found successfully'
            ];
        } else {
            return [
                'success' => false,
                'error' => $data['message']
            ];
        }
    }
}

// Usage
$client = new BrandInfoClient();

$testQueries = [
    'https://www.google.com',
    'microsoft.com',
    'Apple',
    'nonexistent-domain-123.com'
];

foreach ($testQueries as $query) {
    echo "\n🔍 Testing: $query\n";
    $result = $client->resolveBrand($query);
    
    if ($result['success']) {
        echo "✅ Success: " . $result['url'] . "\n";
    } else {
        echo "❌ Error: " . $result['error'] . "\n";
    }
}
?>
```

## Setup and Configuration

### 1. Google Custom Search API Setup (Required for Company Name Search)

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing one
3. Enable the "Custom Search JSON API"
4. Create an API key and restrict it by IP address
5. Create a Custom Search Engine at [cse.google.com](https://cse.google.com/)
6. Get your CSE ID

### 2. Configuration in `application.properties`

```properties
# Replace with your actual values
google.customsearch.api-key=AIzaSyBOti4mM-6x9WDnZIjIeyEU21OpBXqWBgw
google.customsearch.cx=017576662512468239146:omuauf_lfve
```

### 3. Environment Variables (Recommended for Production)

```bash
export GOOGLE_API_KEY=your_actual_api_key
export GOOGLE_CSE_ID=your_actual_cse_id
```

## Testing Without Google API

Even without Google Custom Search API configuration, the API will work for:
- ✅ Direct URL validation (`https://www.google.com`)
- ✅ Domain name resolution (`google.com`, `microsoft.com`)
- ❌ Company name search (will return "Search service is not configured")

## Common Use Cases

### 1. Website Validator
```javascript
async function validateWebsite(url) {
    const result = await getBrandInfo(url);
    return result !== null;
}
```

### 2. Company Website Finder
```javascript
async function findCompanyWebsite(companyName) {
    const result = await getBrandInfo(companyName);
    if (result) {
        return result;
    }
    throw new Error('Company website not found');
}
```

### 3. URL Normalizer
```javascript
async function normalizeUrl(input) {
    const result = await getBrandInfo(input);
    return result || input; // Return original if not found
}
```

### 4. Batch Processing
```javascript
async function processMultipleBrands(queries) {
    const results = await Promise.all(
        queries.map(query => getBrandInfo(query))
    );
    return results.filter(result => result !== null);
}
```

## Rate Limiting Guidelines

- The API has rate limiting enabled for public access
- Default: 50 requests per minute per IP address (configurable)
- When rate limit is exceeded, you'll receive HTTP 429 status
- Implement exponential backoff in your client code

## Best Practices

1. **Always URL encode** your query parameters
2. **Handle errors gracefully** - not all queries will resolve
3. **Implement caching** in your client to reduce API calls
4. **Use appropriate timeouts** (30 seconds recommended)
5. **Respect rate limits** and implement backoff strategies
6. **Validate inputs** before sending to API

## Troubleshooting

### Common Issues:

1. **404 Error**: Check that you're using the correct endpoint path `/auth/brand-info`
2. **Rate Limit**: Implement client-side throttling
3. **Network Timeout**: Increase timeout values in your HTTP client
4. **Google API Errors**: Verify API key and CSE ID configuration
5. **CORS Issues**: Ensure proper CORS configuration if calling from browser

This API is now ready for production use! 🚀
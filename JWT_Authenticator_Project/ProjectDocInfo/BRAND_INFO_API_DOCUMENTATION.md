# Brand Info API Documentation

## Overview

The Brand Info API endpoint (`/auth/brand-info`) is a public RESTful API that intelligently identifies and verifies the official website associated with user input. It accepts URLs, domain names, or company names and returns validated, active website URLs.

## API Endpoint

- **HTTP Method**: GET
- **Endpoint Path**: `/auth/brand-info`
- **Access**: Public (no authentication required)
- **Rate Limiting**: Applied per IP address (more restrictive than authenticated endpoints)

## Request Parameters

| Parameter | Type   | Required | Description |
|-----------|--------|----------|-------------|
| `query`   | String | Yes      | URL, domain name, or company name to resolve |

## Example Requests

```bash
# Direct URL
GET /auth/brand-info?query=https://www.google.com

# Domain name
GET /auth/brand-info?query=microsoft.com

# Company name
GET /auth/brand-info?query=Apple

# Company full name
GET /auth/brand-info?query=Wipro Limited
```

## Response Structure

```json
{
  "status": "success|error",
  "message": "string (optional - only present for errors)",
  "resolvedUrl": "string (optional - only present for success)"
}
```

### Response Fields

- **status**: Indicates the outcome ("success" or "error")
- **message**: Descriptive error message (only present when status is "error")
- **resolvedUrl**: Full, validated, and active URL after any redirects (only present when status is "success")

## Example Responses

### Successful Responses

**Direct URL Resolution:**
```json
{
  "status": "success",
  "resolvedUrl": "https://www.example.com/path"
}
```

**Domain Name Resolution (HTTPS):**
```json
{
  "status": "success",
  "resolvedUrl": "https://www.example.com"
}
```

**Company Name Resolution (via Google Search):**
```json
{
  "status": "success",
  "resolvedUrl": "https://www.apple.com"
}
```

### Error Responses

**Invalid URL Format:**
```json
{
  "status": "error",
  "message": "The provided input is not a valid URL format."
}
```

**Domain Not Found:**
```json
{
  "status": "error",
  "message": "The provided domain name does not have an associated active website."
}
```

**Company Not Found:**
```json
{
  "status": "error",
  "message": "No official website could be found for the provided company name."
}
```

**Network Error:**
```json
{
  "status": "error",
  "message": "A network error occurred while trying to reach the website."
}
```

**Rate Limit Exceeded:**
```json
{
  "error": "Rate limit exceeded. Please try again later.",
  "retryAfter": 60,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## Resolution Logic

The API follows a prioritized resolution flow:

### 1. Direct URL Validation
- Validates URLs starting with `http://` or `https://`
- Uses Apache Commons Validator for URL validation
- Performs HEAD request to verify website existence
- Returns final URL after redirects

### 2. Domain Name Construction
- For inputs that look like domain names (e.g., `example.com`)
- Tries HTTPS first, then HTTP if HTTPS fails
- Performs website existence check
- Returns the working protocol version

### 3. Company Name Search
- Uses Google Custom Search JSON API
- Constructs search query: `{company name} + "official website"`
- Filters out common non-official domains (Wikipedia, social media, etc.)
- Prioritizes results with "official" in title or snippet
- Verifies found URLs for accessibility

## Configuration

### Required Environment Variables

```properties
# Google Custom Search API Configuration
google.customsearch.api-key=${GOOGLE_API_KEY:YOUR_GOOGLE_API_KEY}
google.customsearch.cx=${GOOGLE_CSE_ID:YOUR_GOOGLE_CSE_ID}

# HTTP Client Timeouts
http.client.timeout.connect=5000
http.client.timeout.read=10000
```

### Google Custom Search Setup

1. **Create a Google Cloud Project**
2. **Enable Custom Search JSON API**
3. **Create API Key** with IP restrictions
4. **Create Custom Search Engine (CSE)**
5. **Configure CSE ID** in application properties

## Rate Limiting

- **Public endpoints**: More restrictive rate limiting applied
- **Per IP address**: Tracked using IP-based bucketing
- **Default limit**: Configurable via `app.forward.rate-limit.requests-per-minute`
- **429 status code**: Returned when rate limit exceeded

## Caching

- **In-memory caching**: Enabled using Caffeine cache
- **Cache duration**: 1 hour by default
- **Cache key**: Based on query string
- **Performance**: Reduces API calls and improves response time

## Error Handling

The API provides specific error messages for different scenarios:

- **Empty/null input**: "Input cannot be empty"
- **Invalid URL format**: "The provided input is not a valid URL format."
- **Non-existent domain**: "The provided domain name does not have an associated active website."
- **Company not found**: "No official website could be found for the provided company name."
- **Network issues**: "A network error occurred while trying to reach the website."
- **API quota exceeded**: "An internal service error occurred while searching for the company's website (e.g., Google API quota exceeded)."

## Testing

### Using cURL

```bash
# Test direct URL
curl "http://localhost:8080/myapp/auth/brand-info?query=https://www.google.com"

# Test domain name
curl "http://localhost:8080/myapp/auth/brand-info?query=microsoft.com"

# Test company name
curl "http://localhost:8080/myapp/auth/brand-info?query=Apple"

# Test invalid input
curl "http://localhost:8080/myapp/auth/brand-info?query=invalid-domain-12345.com"
```

### Using JavaScript/Fetch

```javascript
async function getBrandInfo(query) {
    try {
        const response = await fetch(`/myapp/auth/brand-info?query=${encodeURIComponent(query)}`);
        const data = await response.json();
        
        if (data.status === 'success') {
            console.log('Website found:', data.resolvedUrl);
        } else {
            console.log('Error:', data.message);
        }
    } catch (error) {
        console.error('Request failed:', error);
    }
}

// Examples
getBrandInfo('https://www.google.com');
getBrandInfo('microsoft.com');
getBrandInfo('Apple');
```

## Security Considerations

1. **API Key Protection**: Google Custom Search API key should be restricted by IP address
2. **Rate Limiting**: Prevents abuse of the public endpoint
3. **Input Validation**: All inputs are sanitized and validated
4. **Error Handling**: Generic error messages prevent information leakage
5. **HTTPS**: All external requests use HTTPS when possible

## Monitoring and Logging

- **Request logging**: All requests are logged with query and IP address
- **Error logging**: Failed requests are logged with stack traces
- **Cache statistics**: Cache hit/miss rates are recorded
- **API usage tracking**: Google API usage is monitored

## Swagger Documentation

The API is fully documented in Swagger/OpenAPI format. Access the interactive documentation at:

```
http://localhost:8080/myapp/swagger-ui.html
```

Navigate to the "Authentication" section to find the `/auth/brand-info` endpoint with:
- Interactive testing interface
- Request/response examples
- Schema documentation
- Error code explanations

## Troubleshooting

### Common Issues

1. **Google API 400 Error**: Check API key and CSE ID configuration
2. **Rate Limit Issues**: Implement client-side throttling
3. **Network Timeouts**: Check connectivity and increase timeout values
4. **Cache Issues**: Clear cache or restart application
5. **Domain Resolution Failures**: Verify DNS and network connectivity

### Debug Information

Enable debug logging by adding to `application.properties`:

```properties
logging.level.com.example.jwtauthenticator.service.BrandInfoService=DEBUG
```

This will provide detailed information about:
- URL validation steps
- HTTP request/response details
- Google API calls and responses
- Cache operations
- Error conditions

## Performance Optimization

1. **Caching**: Aggressive caching reduces external API calls
2. **Connection Pooling**: HTTP client reuses connections
3. **Timeout Configuration**: Optimized timeouts prevent long waits
4. **Parallel Processing**: Multiple resolution attempts can run concurrently
5. **Rate Limiting**: Prevents service overload

## Future Enhancements

1. **Multiple Search Providers**: Add Bing, DuckDuckGo as fallbacks
2. **Website Metadata Extraction**: Return additional site information
3. **Batch Processing**: Support multiple queries in single request
4. **Webhook Support**: Notify clients of resolution completion
5. **Advanced Filtering**: More sophisticated result filtering algorithms
package com.example.jwtauthenticator.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 📦 Request/Response Data Transfer Object
 * 
 * This DTO holds extracted data from HttpServletRequest and HttpServletResponse
 * to avoid recycling issues in async processing.
 * 
 * @author BrandSnap API Team
 * @version 1.0
 * @since Java 21
 */
@Data
@Builder(toBuilder = true)
public class RequestResponseData {
    
    // Request data
    private String userAgent;
    private String requestMethod;
    private String requestPath;
    private String queryString;
    private Long requestSizeBytes;
    private String targetUrl;
    
    // Response data
    private Integer responseStatus;
    private Long responseSizeBytes;
    
    // Timing data
    private long startTime;
    private long responseTimeMs;
    
    // Cache data
    private String cacheHitType;
    
    // Response body
    private String responseBody;
    
    // Error data
    private String errorMessage;
    
    /**
     * 🏗️ Extract data from HttpServletRequest safely
     */
    public static RequestResponseDataBuilder fromRequest(jakarta.servlet.http.HttpServletRequest request) {
        RequestResponseDataBuilder builder = RequestResponseData.builder();
        
        if (request != null) {
            try {
                builder.userAgent(request.getHeader("User-Agent"))
                       .requestMethod(request.getMethod())
                       .requestPath(request.getRequestURI())
                       .queryString(request.getQueryString());
                
                // Extract target URL from request parameter
                String urlParam = request.getParameter("url");
                if (urlParam != null && !urlParam.trim().isEmpty()) {
                    builder.targetUrl(urlParam.trim());
                }
                
                // Extract request size
                int contentLength = request.getContentLength();
                if (contentLength > 0) {
                    builder.requestSizeBytes((long) contentLength);
                }
                
            } catch (Exception e) {
                // Request may be recycled, ignore errors
            }
        }
        
        return builder;
    }
    
    /**
     * 🏗️ Add response data safely
     */
    public RequestResponseDataBuilder withResponse(jakarta.servlet.http.HttpServletResponse response) {
        RequestResponseDataBuilder builder = this.toBuilder();
        
        if (response != null) {
            try {
                builder.responseStatus(response.getStatus());
                
                // Try to get response size if available
                String contentLengthHeader = response.getHeader("Content-Length");
                if (contentLengthHeader != null) {
                    try {
                        builder.responseSizeBytes(Long.parseLong(contentLengthHeader));
                    } catch (NumberFormatException e) {
                        // Ignore invalid content length
                    }
                }
                
            } catch (Exception e) {
                // Response may be recycled, ignore errors
            }
        }
        
        return builder;
    }
    
    /**
     * 🏗️ Add timing data
     */
    public RequestResponseDataBuilder withTiming(long startTime) {
        return this.toBuilder()
                   .startTime(startTime)
                   .responseTimeMs(System.currentTimeMillis() - startTime);
    }
    
    /**
     * 🏗️ Add cache data
     */
    public RequestResponseDataBuilder withCache(String cacheHitType) {
        return this.toBuilder().cacheHitType(cacheHitType);
    }
    
    /**
     * 🏗️ Add response body
     */
    public RequestResponseDataBuilder withResponseBody(String responseBody) {
        RequestResponseDataBuilder builder = this.toBuilder().responseBody(responseBody);
        
        // Calculate response size from body if not already set
        if (responseBody != null && this.responseSizeBytes == null) {
            builder.responseSizeBytes((long) responseBody.getBytes().length);
        }
        
        return builder;
    }
    
    /**
     * 🏗️ Add error data
     */
    public RequestResponseDataBuilder withError(String errorMessage, Integer responseStatus) {
        return this.toBuilder()
                   .errorMessage(errorMessage)
                   .responseStatus(responseStatus);
    }
}
package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.config.GoogleSearchConfig;
import com.example.jwtauthenticator.dto.BrandInfoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class BrandInfoService {

    private final GoogleSearchConfig googleSearchConfig;
    private final HttpClient httpClient;
    private final UrlValidator urlValidator;
    private final ObjectMapper objectMapper;
    
    // Pattern to check if input looks like a domain (no spaces, contains dots)
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    
    // Common non-official domains to filter out
    private static final List<String> FILTERED_DOMAINS = Arrays.asList(
        "wikipedia.org", "facebook.com", "twitter.com", "linkedin.com", 
        "instagram.com", "youtube.com", "crunchbase.com", "bloomberg.com"
    );

    @Autowired
    public BrandInfoService(GoogleSearchConfig googleSearchConfig) {
        this.googleSearchConfig = googleSearchConfig;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
        this.urlValidator = new UrlValidator(new String[]{"http", "https"});
        this.objectMapper = new ObjectMapper();
    }

    @Cacheable(value = "brandData", key = "#query", cacheManager = "cacheManager")
    public BrandInfoResponse resolveBrandInfo(String query) {
        log.info("Resolving brand info for query: {}", query);
        
        // Input validation
        if (query == null || query.trim().isEmpty()) {
            return BrandInfoResponse.error("Input cannot be empty");
        }
        
        String trimmedQuery = query.trim();
        
        try {
            // Attempt 1: Direct URL validation
            if (trimmedQuery.startsWith("http://") || trimmedQuery.startsWith("https://")) {
                if (urlValidator.isValid(trimmedQuery)) {
                    return checkWebsiteExistence(trimmedQuery);
                } else {
                    return BrandInfoResponse.error("The provided input is not a valid URL format.");
                }
            }
            
            // Attempt 2: Domain name construction & validation
            if (DOMAIN_PATTERN.matcher(trimmedQuery).matches()) {
                // Try HTTPS first
                String httpsUrl = "https://" + trimmedQuery;
                BrandInfoResponse httpsResult = checkWebsiteExistence(httpsUrl);
                if ("success".equals(httpsResult.getStatus())) {
                    return httpsResult;
                }
                
                // Try HTTP if HTTPS fails
                String httpUrl = "http://" + trimmedQuery;
                BrandInfoResponse httpResult = checkWebsiteExistence(httpUrl);
                if ("success".equals(httpResult.getStatus())) {
                    return httpResult;
                }
            }
            
            // Attempt 3: Company name search using Google Custom Search
            return searchCompanyWebsite(trimmedQuery);
            
        } catch (Exception e) {
            log.error("Error resolving brand info for query: {}", query, e);
            return BrandInfoResponse.error("An internal error occurred while processing the request.");
        }
    }

    private BrandInfoResponse checkWebsiteExistence(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                // Get the final URL after redirects
                String finalUrl = response.uri().toString();
                log.info("Website exists: {} -> {}", url, finalUrl);
                return BrandInfoResponse.success(finalUrl);
            } else {
                log.warn("Website returned non-success status: {} for URL: {}", response.statusCode(), url);
                return BrandInfoResponse.error("The provided domain name does not have an associated active website.");
            }
            
        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            log.warn("Connection failed for URL: {} - {}", url, e.getMessage());
            return BrandInfoResponse.error("The provided domain name does not have an associated active website.");
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("Timeout occurred for URL: {}", url);
            return BrandInfoResponse.error("A network error occurred while trying to reach the website.");
        } catch (Exception e) {
            log.error("Error checking website existence for URL: {}", url, e);
            return BrandInfoResponse.error("A network error occurred while trying to reach the website.");
        }
    }

    private BrandInfoResponse searchCompanyWebsite(String companyName) {
        try {
            // Check if API key and CSE ID are configured
            if ("YOUR_GOOGLE_API_KEY".equals(googleSearchConfig.getApiKey()) || 
                googleSearchConfig.getApiKey() == null || 
                googleSearchConfig.getApiKey().trim().isEmpty()) {
                log.warn("Google Custom Search API key not configured");
                return BrandInfoResponse.error("Search service is not configured.");
            }
            
            if ("YOUR_GOOGLE_CSE_ID".equals(googleSearchConfig.getCx()) || 
                googleSearchConfig.getCx() == null || 
                googleSearchConfig.getCx().trim().isEmpty()) {
                log.warn("Google Custom Search Engine ID not configured");
                return BrandInfoResponse.error("Search service is not configured.");
            }

            String searchQuery = URLEncoder.encode(companyName + " official website", StandardCharsets.UTF_8);
            String apiUrl = String.format(
                "https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&q=%s&num=10",
                googleSearchConfig.getApiKey(),
                googleSearchConfig.getCx(),
                searchQuery
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "BrandInfo-Service/1.0")
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseSearchResults(response.body());
            } else if (response.statusCode() == 429) {
                log.warn("Google API quota exceeded");
                return BrandInfoResponse.error("An internal service error occurred while searching for the company's website (e.g., Google API quota exceeded).");
            } else {
                log.error("Google API returned status: {} for query: {}", response.statusCode(), companyName);
                return BrandInfoResponse.error("An internal service error occurred while searching for the company's website (e.g., Google API quota exceeded).");
            }
            
        } catch (Exception e) {
            log.error("Error searching for company website: {}", companyName, e);
            return BrandInfoResponse.error("An internal service error occurred while searching for the company's website (e.g., Google API quota exceeded).");
        }
    }

    private BrandInfoResponse parseSearchResults(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode items = root.get("items");
            
            if (items == null || !items.isArray() || items.size() == 0) {
                return BrandInfoResponse.error("No official website could be found for the provided company name.");
            }

            // Look for the best match
            for (JsonNode item : items) {
                String link = item.get("link").asText();
                String title = item.get("title").asText().toLowerCase();
                String snippet = item.get("snippet").asText().toLowerCase();
                
                // Skip filtered domains
                if (isFilteredDomain(link)) {
                    continue;
                }
                
                // Prioritize results with "official" in title or snippet
                if (title.contains("official") || snippet.contains("official") || 
                    title.contains("home") || title.contains("company")) {
                    
                    BrandInfoResponse result = checkWebsiteExistence(link);
                    if ("success".equals(result.getStatus())) {
                        return result;
                    }
                }
            }
            
            // If no "official" result found, try the first non-filtered result
            for (JsonNode item : items) {
                String link = item.get("link").asText();
                
                if (!isFilteredDomain(link)) {
                    BrandInfoResponse result = checkWebsiteExistence(link);
                    if ("success".equals(result.getStatus())) {
                        return result;
                    }
                }
            }
            
            return BrandInfoResponse.error("No official website could be found for the provided company name.");
            
        } catch (Exception e) {
            log.error("Error parsing search results", e);
            return BrandInfoResponse.error("An internal service error occurred while searching for the company's website (e.g., Google API quota exceeded).");
        }
    }

    private boolean isFilteredDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null) {
                return FILTERED_DOMAINS.stream().anyMatch(host::contains);
            }
        } catch (Exception e) {
            log.warn("Invalid URL format: {}", url);
        }
        return false;
    }
}
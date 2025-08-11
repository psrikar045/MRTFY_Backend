package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.config.ForwardConfig;
import com.example.jwtauthenticator.dto.BrandExtractionResponse;
import com.example.jwtauthenticator.entity.Brand;
import com.example.jwtauthenticator.entity.BrandAsset;
import com.example.jwtauthenticator.entity.BrandColor;
import com.example.jwtauthenticator.entity.BrandFont;
import com.example.jwtauthenticator.entity.BrandImage;
import com.example.jwtauthenticator.entity.BrandSocialLink;
import com.example.jwtauthenticator.repository.BrandRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.example.jwtauthenticator.entity.ApiKey; // PHASE 3 INTEGRATION
import jakarta.servlet.http.HttpServletRequest; // PHASE 3 INTEGRATION
import jakarta.servlet.http.HttpServletResponse; // PHASE 3 INTEGRATION
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForwardService {

    private static final String EXTERNAL_API ="http://202.65.155.117:3000/api/extract-company-details";
    private final WebClient forwardWebClient;
    private final Cache<String, String> forwardCache;
    private final ForwardConfig forwardConfig;
    private final BrandExtractionService brandExtractionService;
    private final BrandRepository brandRepository;
    private final ObjectMapper objectMapper;
    private final RivoFetchLoggingService rivoFetchLoggingService; // PHASE 3 INTEGRATION
    
    @Value("${app.brand-extraction.enabled:true}")
    private boolean brandExtractionEnabled;

    public CompletableFuture<ResponseEntity<String>> forward(String url) {
        // First check if the URL exists in the brands table
        Optional<Brand> existingBrand = findBrandByUrl(url);
        if (existingBrand.isPresent()) {
            log.info("Found cached brand data for URL: {}", url);
            try {
                BrandExtractionResponse response = convertBrandToExtractionResponse(existingBrand.get());
                String jsonResponse = objectMapper.writeValueAsString(response);
                return CompletableFuture.completedFuture(ResponseEntity.ok(jsonResponse));
            } catch (Exception e) {
                log.error("Error converting brand data to response for URL: {}", url, e);
                // Fall through to external API call on error
            }
        }

        // Check in-memory cache as fallback
        String cached = forwardCache.getIfPresent(url);
        if (cached != null) {
            log.info("Found in-memory cached data for URL: {}", url);
            // Even for cached responses, trigger brand extraction if enabled
            if (brandExtractionEnabled) {
                triggerBrandExtraction(url, cached);
            }
            return CompletableFuture.completedFuture(ResponseEntity.ok(cached));
        }

        // Make external API call only if not found in database or cache
        log.info("Making external API call for URL: {}", url);
        return forwardWebClient.post()
                .uri(EXTERNAL_API)
                .bodyValue(Collections.singletonMap("url", url))
                .exchangeToMono(resp -> resp.bodyToMono(String.class)
                        .map(body -> ResponseEntity.status(resp.statusCode()).body(body)))
                .timeout(Duration.ofSeconds(forwardConfig.getTimeoutSeconds()))
                .doOnError(e -> log.error("Forwarding error", e))
                .toFuture()
                .thenApply(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        forwardCache.put(url, response.getBody());
                        
                        // Trigger brand data extraction for successful responses
                        if (brandExtractionEnabled) {
                            triggerBrandExtraction(url, response.getBody());
                        }
                    }
                    return response;
                });
    }
    
    /**
     * 🚀 PHASE 3: Forward with RivoFetch Logging Integration
     * 
     * Enhanced forward method that includes comprehensive RivoFetch logging
     * for analytics and monitoring purposes.
     * 
     * @param url URL to forward to external API
     * @param request HTTP servlet request for context extraction
     * @param response HTTP servlet response for logging
     * @param apiKey API key used for the request
     * @return CompletableFuture with response entity
     */
    public CompletableFuture<ResponseEntity<String>> forwardWithLogging(
            String url, 
            HttpServletRequest request, 
            HttpServletResponse response, 
            ApiKey apiKey) {
        
        long startTime = System.currentTimeMillis();
        
        // First check if the URL exists in the brands table
        Optional<Brand> existingBrand = findBrandByUrl(url);
        if (existingBrand.isPresent()) {
            log.info("Found cached brand data for URL: {}", url);
            try {
                BrandExtractionResponse brandResponse = convertBrandToExtractionResponse(existingBrand.get());
                String jsonResponse = objectMapper.writeValueAsString(brandResponse);
                
                // Log successful cached response (DATABASE_HIT)
                rivoFetchLoggingService.logSuccessfulRivoFetchAsync(
                        request, response, apiKey, startTime, jsonResponse, "DATABASE_HIT");
                
                return CompletableFuture.completedFuture(ResponseEntity.ok(jsonResponse));
            } catch (Exception e) {
                log.error("Error converting brand data to response for URL: {}", url, e);
                
                // Log the error
                rivoFetchLoggingService.logFailedRivoFetchAsync(
                        request, apiKey, startTime, 
                        "Brand data conversion error: " + e.getMessage(), 500);
                
                // Fall through to external API call on error
            }
        }

        // Check in-memory cache as fallback
        String cached = forwardCache.getIfPresent(url);
        if (cached != null) {
            log.info("Found in-memory cached data for URL: {}", url);
            
            // Log successful cached response (MEMORY_HIT)
            rivoFetchLoggingService.logSuccessfulRivoFetchAsync(
                    request, response, apiKey, startTime, cached, "MEMORY_HIT");
            
            // Even for cached responses, trigger brand extraction if enabled
            if (brandExtractionEnabled) {
                triggerBrandExtraction(url, cached);
            }
            return CompletableFuture.completedFuture(ResponseEntity.ok(cached));
        }

        // Make external API call only if not found in database or cache
        log.info("Making external API call for URL: {}", url);
        return forwardWebClient.post()
                .uri(EXTERNAL_API)
                .bodyValue(Collections.singletonMap("url", url))
                .exchangeToMono(resp -> resp.bodyToMono(String.class)
                        .map(body -> ResponseEntity.status(resp.statusCode()).body(body)))
                .timeout(Duration.ofSeconds(forwardConfig.getTimeoutSeconds()))
                .doOnError(e -> {
                    log.error("Forwarding error for URL: {}", url, e);
                    
                    // Log the error asynchronously
                    rivoFetchLoggingService.logFailedRivoFetchAsync(
                            request, apiKey, startTime, 
                            "External API error: " + e.getMessage(), 500);
                })
                .toFuture()
                .thenApply(forwardResponse -> {
                    if (forwardResponse.getStatusCode().is2xxSuccessful()) {
                        // Cache successful response
                        forwardCache.put(url, forwardResponse.getBody());
                        
                        // Log successful response (MISS - external API call)
                        rivoFetchLoggingService.logSuccessfulRivoFetchAsync(
                                request, response, apiKey, startTime, forwardResponse.getBody(), "MISS");
                        
                        // Trigger brand data extraction for successful responses
                        if (brandExtractionEnabled) {
                            triggerBrandExtraction(url, forwardResponse.getBody());
                        }
                    } else {
                        // Log failed response
                        rivoFetchLoggingService.logFailedRivoFetchAsync(
                                request, apiKey, startTime, 
                                "External API returned status: " + forwardResponse.getStatusCode(),
                                forwardResponse.getStatusCode().value());
                    }
                    return forwardResponse;
                })
                .exceptionally(throwable -> {
                    log.error("Exception in forward operation for URL: {}", url, throwable);
                    
                    // Log the exception
                    rivoFetchLoggingService.logFailedRivoFetchAsync(
                            request, apiKey, startTime, 
                            "Forward operation exception: " + throwable.getMessage(), 500);
                    
                    // Return error response
                    return ResponseEntity.status(500).body("Internal server error");
                });
    }
    
    /**
     * 🚀 PHASE 3: Forward with RivoFetch Logging for Public Requests
     */
    public CompletableFuture<ResponseEntity<String>> forwardWithPublicLogging(
            String url, 
            HttpServletRequest request, 
            HttpServletResponse response) {
        
        long startTime = System.currentTimeMillis();
        
        // First check if the URL exists in the brands table
        Optional<Brand> existingBrand = findBrandByUrl(url);
        if (existingBrand.isPresent()) {
            log.info("Found cached brand data for URL: {}", url);
            try {
                BrandExtractionResponse brandResponse = convertBrandToExtractionResponse(existingBrand.get());
                String jsonResponse = objectMapper.writeValueAsString(brandResponse);
                
                // Log successful cached response (DATABASE_HIT)
                rivoFetchLoggingService.logSuccessfulPublicRivoFetchAsync(
                        request, response, startTime, jsonResponse, "DATABASE_HIT");
                
                return CompletableFuture.completedFuture(ResponseEntity.ok(jsonResponse));
            } catch (Exception e) {
                log.error("Error converting brand data to response for URL: {}", url, e);
                
                // Log the error
                rivoFetchLoggingService.logFailedPublicRivoFetchAsync(
                        request, startTime, 
                        "Brand data conversion error: " + e.getMessage(), 500);
                
                // Fall through to external API call on error
            }
        }

        // Check in-memory cache as fallback
        String cached = forwardCache.getIfPresent(url);
        if (cached != null) {
            log.info("Found in-memory cached data for URL: {}", url);
            
            // Log successful cached response (MEMORY_HIT)
            rivoFetchLoggingService.logSuccessfulPublicRivoFetchAsync(
                    request, response, startTime, cached, "MEMORY_HIT");
            
            // Even for cached responses, trigger brand extraction if enabled
            if (brandExtractionEnabled) {
                triggerBrandExtraction(url, cached);
            }
            return CompletableFuture.completedFuture(ResponseEntity.ok(cached));
        }

        // Make external API call only if not found in database or cache
        log.info("Making external API call for URL: {}", url);
        return forwardWebClient.post()
                .uri(EXTERNAL_API)
                .bodyValue(Collections.singletonMap("url", url))
                .exchangeToMono(resp -> resp.bodyToMono(String.class)
                        .map(body -> ResponseEntity.status(resp.statusCode()).body(body)))
                .timeout(Duration.ofSeconds(forwardConfig.getTimeoutSeconds()))
                .doOnError(e -> {
                    log.error("Forwarding error for URL: {}", url, e);
                    
                    // Log the error asynchronously
                    rivoFetchLoggingService.logFailedPublicRivoFetchAsync(
                            request, startTime, 
                            "External API error: " + e.getMessage(), 500);
                })
                .toFuture()
                .thenApply(forwardResponse -> {
                    if (forwardResponse.getStatusCode().is2xxSuccessful()) {
                        // Cache successful response
                        forwardCache.put(url, forwardResponse.getBody());
                        
                        // Log successful response (MISS - external API call)
                        rivoFetchLoggingService.logSuccessfulPublicRivoFetchAsync(
                                request, response, startTime, forwardResponse.getBody(), "MISS");
                        
                        // Trigger brand data extraction for successful responses
                        if (brandExtractionEnabled) {
                            triggerBrandExtraction(url, forwardResponse.getBody());
                        }
                    } else {
                        // Log failed response
                        rivoFetchLoggingService.logFailedPublicRivoFetchAsync(
                                request, startTime, 
                                "External API returned status: " + forwardResponse.getStatusCode(),
                                forwardResponse.getStatusCode().value());
                    }
                    return forwardResponse;
                })
                .exceptionally(throwable -> {
                    log.error("Exception in forward operation for URL: {}", url, throwable);
                    
                    // Log the exception
                    rivoFetchLoggingService.logFailedPublicRivoFetchAsync(
                            request, startTime, 
                            "Forward operation exception: " + throwable.getMessage(), 500);
                    
                    // Return error response
                    return ResponseEntity.status(500).body("Internal server error");
                });
    }
    
    /**
     * Trigger brand data extraction asynchronously
     */
    private void triggerBrandExtraction(String url, String apiResponse) {
        try {
            log.info("Triggering brand extraction for URL: {}", url);
            Brand brand = brandExtractionService.extractAndStoreBrandData(url, apiResponse);
            log.info("Brand extraction completed for: {} (Brand ID: {})", url, brand.getId());
        } catch (Exception e) {
            log.error("Brand extraction failed for URL: {}", url, e);
            // Don't throw the exception as this shouldn't affect the main forward operation
        }
    }

    /**
     * Convert Brand entity data to BrandExtractionResponse format
     */
    private BrandExtractionResponse convertBrandToExtractionResponse(Brand brand) {
        BrandExtractionResponse response = new BrandExtractionResponse();
        
        // Set basic message
        response.setMessage(brand.getExtractionMessage() != null ? 
            brand.getExtractionMessage() : "Data retrieved from cache");
        
        // Set company data
        BrandExtractionResponse.CompanyData companyData = new BrandExtractionResponse.CompanyData();
        companyData.setName(brand.getName());
        companyData.setDescription(brand.getDescription());
        companyData.setIndustry(brand.getIndustry());
        companyData.setLocation(brand.getLocation());
        companyData.setFounded(brand.getFounded());
        companyData.setCompanyType(brand.getCompanyType());
        companyData.setEmployees(brand.getEmployees());
        companyData.setWebsite(brand.getWebsite());
        
        // Set new fields with smart mapping
        companyData.setCompanySize(brand.getEmployees());
        companyData.setHeadquarters(brand.getLocation());
        companyData.setType(brand.getCompanyType());
        
        // Parse JSON fields back to lists for performance
        try {
            if (brand.getSpecialties() != null && !brand.getSpecialties().trim().isEmpty()) {
                List<String> specialties = objectMapper.readValue(brand.getSpecialties(), 
                    new TypeReference<List<String>>() {});
                companyData.setSpecialties(specialties);
            }
            if (brand.getLocations() != null && !brand.getLocations().trim().isEmpty()) {
                List<String> locations = objectMapper.readValue(brand.getLocations(), 
                    new TypeReference<List<String>>() {});
                companyData.setLocations(locations);
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize specialties/locations for brand: {}", brand.getName(), e);
        }
        
        // Convert social links
        Map<String, String> socialLinks = new HashMap<>();
        for (BrandSocialLink socialLink : brand.getSocialLinks()) {
            if (socialLink.getUrl() != null && !socialLink.getUrl().isEmpty()) {
                socialLinks.put(socialLink.getPlatform().toString().toLowerCase(), socialLink.getUrl());
            }
            if (socialLink.getExtractionError() != null && 
                socialLink.getPlatform() == BrandSocialLink.Platform.LINKEDIN) {
                companyData.setLinkedInError(socialLink.getExtractionError());
            }
        }
        companyData.setSocialLinks(socialLinks);
        response.setCompany(companyData);
        
        // Set logo data from assets
        BrandExtractionResponse.LogoData logoData = new BrandExtractionResponse.LogoData();
        for (BrandAsset asset : brand.getAssets()) {
            switch (asset.getAssetType()) {
                case LOGO:
                    logoData.setLogo(asset.getOriginalUrl());
                    break;
                case SYMBOL:
                    logoData.setSymbol(asset.getOriginalUrl());
                    break;
                case ICON:
                    logoData.setIcon(asset.getOriginalUrl());
                    break;
                case BANNER:
                    logoData.setBanner(asset.getOriginalUrl());
                    break;
                case LINKEDIN_BANNER:
                    logoData.setLinkedInBanner(asset.getOriginalUrl());
                    break;
                case LINKEDIN_LOGO:
                    logoData.setLinkedInLogo(asset.getOriginalUrl());
                    break;
            }
        }
        response.setLogo(logoData);
        
        // Set colors
        List<BrandExtractionResponse.ColorData> colors = brand.getColors().stream()
            .map(this::convertBrandColorToColorData)
            .collect(Collectors.toList());
        response.setColors(colors);
        
        // Set fonts
        List<BrandExtractionResponse.FontData> fonts = brand.getFonts().stream()
            .map(this::convertBrandFontToFontData)
            .collect(Collectors.toList());
        response.setFonts(fonts);
        
        // Set images
        List<BrandExtractionResponse.ImageData> images = brand.getImages().stream()
            .map(this::convertBrandImageToImageData)
            .collect(Collectors.toList());
        response.setImages(images);
        
        // Set performance data
        BrandExtractionResponse.PerformanceData performanceData = new BrandExtractionResponse.PerformanceData();
        performanceData.setExtractionTimeSeconds(brand.getExtractionTimeSeconds());
        performanceData.setTimestamp(brand.getLastExtractionTimestamp());
        response.setPerformance(performanceData);
        
        return response;
    }
    
    private BrandExtractionResponse.ColorData convertBrandColorToColorData(BrandColor brandColor) {
        BrandExtractionResponse.ColorData colorData = new BrandExtractionResponse.ColorData();
        colorData.setHex(brandColor.getHexCode());
        colorData.setRgb(brandColor.getRgbValue());
        colorData.setBrightness(brandColor.getBrightness());
        colorData.setName(brandColor.getColorName());
        return colorData;
    }
    
    private BrandExtractionResponse.FontData convertBrandFontToFontData(BrandFont brandFont) {
        BrandExtractionResponse.FontData fontData = new BrandExtractionResponse.FontData();
        fontData.setName(brandFont.getFontName());
        fontData.setType(brandFont.getFontType());
        fontData.setStack(brandFont.getFontStack());
        return fontData;
    }
    
    private BrandExtractionResponse.ImageData convertBrandImageToImageData(BrandImage brandImage) {
        BrandExtractionResponse.ImageData imageData = new BrandExtractionResponse.ImageData();
        imageData.setSrc(brandImage.getSourceUrl());
        imageData.setAlt(brandImage.getAltText());
        return imageData;
    }

    /**
     * Find brand by URL with improved matching logic
     */
    private Optional<Brand> findBrandByUrl(String url) {
        // Try exact match first
        Optional<Brand> exactMatch = brandRepository.findByWebsite(url);
        if (exactMatch.isPresent()) {
            return exactMatch;
        }
        
        // Try normalized URL matching using database query
        return brandRepository.findByNormalizedWebsite(url);
    }

    public ForwardConfig getForwardConfig() {
        return forwardConfig;
    }
}

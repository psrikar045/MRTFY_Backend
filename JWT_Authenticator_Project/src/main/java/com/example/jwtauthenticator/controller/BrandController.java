package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.BrandDataResponse;
import com.example.jwtauthenticator.entity.Brand;
import com.example.jwtauthenticator.entity.BrandAsset;
import com.example.jwtauthenticator.entity.BrandImage;
import com.example.jwtauthenticator.repository.BrandAssetRepository;
import com.example.jwtauthenticator.repository.BrandImageRepository;
import com.example.jwtauthenticator.repository.BrandRepository;
import com.example.jwtauthenticator.service.BrandDataService;
import com.example.jwtauthenticator.service.BrandExtractionService;
import com.example.jwtauthenticator.service.BrandManagementService;
import com.example.jwtauthenticator.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Brand Data", description = "Endpoints for retrieving stored brand information")
public class BrandController {
    
    private final BrandDataService brandDataService;
    private final BrandExtractionService brandExtractionService;
    private final BrandManagementService brandManagementService;
    private final FileStorageService fileStorageService;
    private final BrandAssetRepository brandAssetRepository;
    private final BrandImageRepository brandImageRepository;
    private final BrandRepository brandRepository;
    
    @GetMapping("/{id}")
    @Operation(
        summary = "Get brand by ID",
        description = "Retrieve complete brand information including assets, colors, fonts, and social links",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Brand found", 
                    content = @Content(schema = @Schema(implementation = BrandDataResponse.class))),
        @ApiResponse(responseCode = "404", description = "Brand not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getBrandById(
            @Parameter(description = "Brand ID", required = true)
            @PathVariable Long id) {
        
        Optional<BrandDataResponse> brand = brandDataService.getBrandById(id);
        
        if (brand.isPresent()) {
            return ResponseEntity.ok(brand.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("Brand not found with ID: " + id));
        }
    }
    
    @GetMapping("/by-website")
    @Operation(
        summary = "Get brand by website URL",
        description = "Retrieve brand information using the website URL",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Brand found"),
        @ApiResponse(responseCode = "404", description = "Brand not found"),
        @ApiResponse(responseCode = "400", description = "Invalid website URL")
    })
    public ResponseEntity<?> getBrandByWebsite(
            @Parameter(description = "Website URL", required = true, example = "https://versa-networks.com")
            @RequestParam String website) {
        
        if (website == null || website.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Website URL is required"));
        }
        
        Optional<BrandDataResponse> brand = brandDataService.getBrandByWebsite(website.trim());
        
        if (brand.isPresent()) {
            return ResponseEntity.ok(brand.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("Brand not found for website: " + website));
        }
    }
    
    @GetMapping("/by-name")
    @Operation(
        summary = "Get brand by name",
        description = "Retrieve brand information using the brand name (case-insensitive)",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    public ResponseEntity<?> getBrandByName(
            @Parameter(description = "Brand name", required = true, example = "Versa Networks")
            @RequestParam String name) {
        
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Brand name is required"));
        }
        
        Optional<BrandDataResponse> brand = brandDataService.getBrandByName(name.trim());
        
        if (brand.isPresent()) {
            return ResponseEntity.ok(brand.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("Brand not found with name: " + name));
        }
    }
    
    @GetMapping("/search")
    @Operation(
        summary = "Search brands",
        description = "Search brands by name, website, description, or industry",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    public ResponseEntity<Page<BrandDataResponse>> searchBrands(
            @Parameter(description = "Search term", required = true)
            @RequestParam String q,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, Math.min(size, 100)); // Limit max size to 100
        Page<BrandDataResponse> brands = brandDataService.searchBrands(q, pageable);
        
        return ResponseEntity.ok(brands);
    }
    
    @GetMapping
    @Operation(
        summary = "Get all brands",
        description = "Retrieve all brands with pagination",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    public ResponseEntity<Page<BrandDataResponse>> getAllBrands(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<BrandDataResponse> brands = brandDataService.getAllBrands(pageable);
        
        return ResponseEntity.ok(brands);
    }
    
    @GetMapping("/by-domain")
    @Operation(
        summary = "Get brands by domain",
        description = "Find brands containing the specified domain pattern",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    public ResponseEntity<List<BrandDataResponse>> getBrandsByDomain(
            @Parameter(description = "Domain pattern", required = true, example = "versa-networks.com")
            @RequestParam String domain) {
        
        List<BrandDataResponse> brands = brandDataService.getBrandsByDomain(domain);
        return ResponseEntity.ok(brands);
    }
    
    @GetMapping("/statistics")
    @Operation(
        summary = "Get brand statistics",
        description = "Retrieve statistics about stored brands",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    public ResponseEntity<BrandDataService.BrandStatistics> getBrandStatistics() {
        BrandDataService.BrandStatistics stats = brandDataService.getBrandStatistics();
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/assets/{assetId}")
    @Operation(
        summary = "Serve brand asset file",
        description = "Serve a brand asset file (logo, icon, etc.) by asset ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Asset file served"),
        @ApiResponse(responseCode = "404", description = "Asset not found"),
        @ApiResponse(responseCode = "500", description = "Error serving file")
    })
    public ResponseEntity<Resource> serveBrandAsset(
            @Parameter(description = "Asset ID", required = true)
            @PathVariable Long assetId) {
        
        try {
            Optional<BrandAsset> assetOpt = brandAssetRepository.findById(assetId);
            
            if (assetOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            BrandAsset asset = assetOpt.get();
            
            // If file is not downloaded yet, return the original URL as redirect
            if (asset.getDownloadStatus() != BrandAsset.DownloadStatus.COMPLETED || 
                asset.getStoredPath() == null) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, asset.getOriginalUrl())
                        .build();
            }
            
            // Serve the stored file
            Resource resource = fileStorageService.getFileAsResource(asset.getStoredPath());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(asset.getMimeType() != null ? 
                            asset.getMimeType() : "application/octet-stream"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "inline; filename=\"" + asset.getFileName() + "\"")
                    .body(resource);
            
        } catch (IOException e) {
            log.error("Error serving brand asset file: {}", assetId, e);
            // Fallback to original URL
            Optional<BrandAsset> assetOpt = brandAssetRepository.findById(assetId);
            if (assetOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, assetOpt.get().getOriginalUrl())
                        .build();
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Unexpected error serving brand asset: {}", assetId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/images/{imageId}")
    @Operation(
        summary = "Serve brand image file",
        description = "Serve a brand image file by image ID"
    )
    public ResponseEntity<Resource> serveBrandImage(
            @Parameter(description = "Image ID", required = true)
            @PathVariable Long imageId) {
        
        try {
            Optional<BrandImage> imageOpt = brandImageRepository.findById(imageId);
            
            if (imageOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            BrandImage image = imageOpt.get();
            
            // If file is not downloaded yet, return the original URL as redirect
            if (image.getDownloadStatus() != BrandImage.DownloadStatus.COMPLETED || 
                image.getStoredPath() == null) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, image.getSourceUrl())
                        .build();
            }
            
            // Serve the stored file
            Resource resource = fileStorageService.getFileAsResource(image.getStoredPath());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(image.getMimeType() != null ? 
                            image.getMimeType() : "application/octet-stream"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "inline; filename=\"" + image.getFileName() + "\"")
                    .body(resource);
            
        } catch (IOException e) {
            log.error("Error serving brand image file: {}", imageId, e);
            // Fallback to original URL
            Optional<BrandImage> imageOpt = brandImageRepository.findById(imageId);
            if (imageOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, imageOpt.get().getSourceUrl())
                        .build();
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Unexpected error serving brand image: {}", imageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/extract")
    @Operation(
        summary = "Manual brand extraction",
        description = "Manually trigger brand data extraction for a given URL (for testing purposes)",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Brand extraction completed"),
        @ApiResponse(responseCode = "400", description = "Invalid URL or extraction failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> extractBrandData(
            @Parameter(description = "URL to extract brand data from", required = true)
            @RequestParam String url,
            @Parameter(description = "Mock API response (for testing)", required = false)
            @RequestParam(required = false) String mockResponse) {
        
        try {
            if (url == null || url.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("URL is required"));
            }
            
            String apiResponse = mockResponse;
            
            // If no mock response provided, use the sample response for testing
            if (apiResponse == null) {
                apiResponse = """
                    {
                        "Logo": {
                            "Logo": "https://versa-networks.com/wordpress/wp-content/themes/bootstrap-theme/assets/images/new-homepage/versa-new-logo.svg",
                            "Symbol": null,
                            "Icon": "https://versa-networks.com/wordpress/wp-content/uploads/2022/06/cropped-versa-logo-transparent-32x32.png",
                            "Banner": null
                        },
                        "Colors": [
                            {"hex": "#ffffff", "rgb": "rgb(255,255,255)", "brightness": 255, "name": "button_text"},
                            {"hex": "#009bdf", "rgb": "rgb(0,155,223)", "brightness": 116, "name": "button_bg"},
                            {"hex": "#0280c6", "rgb": "rgb(2,128,198)", "brightness": 98, "name": "button_bg"},
                            {"hex": "#515254", "rgb": "rgb(81,82,84)", "brightness": 82, "name": "h1_text"}
                        ],
                        "Fonts": [
                            {"name": "Gilroy", "type": "heading", "stack": "Gilroy, sans-serif"},
                            {"name": "Inter", "type": "body", "stack": "Inter, sans-serif"}
                        ],
                        "Images": [
                            {"src": "https://versa-networks.com/wordpress/wp-content/themes/bootstrap-theme/assets/images/new-homepage/gartner-peer-insights.webp", "alt": "Gartner Peer Insights logo"},
                            {"src": "https://versa-networks.com/wordpress/wp-content/themes/bootstrap-theme/assets/images/new-homepage/bio-raid-logo.webp", "alt": "Content Image"}
                        ],
                        "Company": {
                            "Name": "Versa Networks",
                            "Description": null,
                            "Industry": null,
                            "Location": null,
                            "Founded": null,
                            "CompanyType": null,
                            "Employees": null,
                            "Website": "https://versa-networks.com/",
                            "SocialLinks": {
                                "Twitter": "https://twitter.com/versanetworks",
                                "LinkedIn": "https://www.linkedin.com/company/versa-networks/mycompany/",
                                "Facebook": "https://www.facebook.com/VersaNetworks",
                                "YouTube": "https://www.youtube.com/channel/UCyDTShxgA6IUqanC92bBF0g/videos"
                            },
                            "LinkedInError": "LinkedIn extraction timeout after 2 minutes"
                        },
                        "_performance": {
                            "extractionTimeSeconds": 126.099,
                            "timestamp": "2025-07-12T09:45:35.844Z"
                        },
                        "_message": "Data extracted dynamically. Accuracy may vary based on website structure."
                    }
                    """;
            }
            
            Brand brand = brandExtractionService.extractAndStoreBrandData(url.trim(), apiResponse);
            
            // Convert to response DTO
            Optional<BrandDataResponse> brandResponse = brandDataService.getBrandById(brand.getId());
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Brand extraction completed successfully");
            result.put("brandId", brand.getId());
            result.put("brandData", brandResponse.orElse(null));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Manual brand extraction failed for URL: {}", url, e);
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Brand extraction failed: " + e.getMessage()));
        }
    }
    
    @PutMapping("/{id}/claim")
    @Operation(
        summary = "Claim a brand",
        description = "Mark a brand as claimed by setting isBrandClaimed to true",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Brand successfully claimed"),
        @ApiResponse(responseCode = "400", description = "Brand is already claimed"),
        @ApiResponse(responseCode = "404", description = "Brand not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> claimBrand(
            @Parameter(description = "Brand ID", required = true)
            @PathVariable Long id) {
        
        boolean claimed = brandManagementService.claimBrand(id);
        
        if (claimed) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Brand successfully claimed");
            response.put("brandId", id);
            response.put("timestamp", java.time.Instant.now().toString());
            return ResponseEntity.ok(response);
        } else {
            // Check if the brand exists
            if (brandDataService.getBrandById(id).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Brand is already claimed"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("Brand not found with ID: " + id));
            }
        }
    }
    
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", java.time.Instant.now().toString());
        return error;
    }
    
    /**
     * Endpoint 1: Get All Brands
     * Path: /brands
     * Method: GET
     */
    @GetMapping("/all")
    @Operation(
        summary = "Get all brands",
        description = "Retrieve a list of all brands from the brands table",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved brands"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getAllBrands() {
        try {
            List<Brand> brands = brandRepository.findAll();
            List<BrandDataResponse> response = brands.stream()
                .map(brandDataService::convertToResponse)
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error retrieving all brands", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Error retrieving brands: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 2: Get Brands by Category
     * Path: /brands/category/:categoryId
     * Method: GET
     */
    @GetMapping("/category/{categoryId}")
    @Operation(
        summary = "Get brands by category",
        description = "Retrieve a list of brands associated with the provided categoryId",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved brands"),
        @ApiResponse(responseCode = "400", description = "Invalid category ID"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getBrandsByCategory(
            @Parameter(description = "Category ID", required = true)
            @PathVariable Long categoryId) {
        
        try {
            if (categoryId == null || categoryId <= 0) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Invalid category ID"));
            }
            
            List<Brand> brands = brandRepository.findByCategoryId(categoryId);
            
            List<BrandDataResponse> response = brands.stream()
                .map(brandDataService::convertToResponse)
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error retrieving brands by category ID: {}", categoryId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Error retrieving brands: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 3: Get Brands by Category and Subcategory
     * Path: /brands/category/:categoryId/subcategory/:subCategoryId
     * Method: GET
     */
    @GetMapping("/category/{categoryId}/subcategory/{subCategoryId}")
    @Operation(
        summary = "Get brands by category and subcategory",
        description = "Retrieve a list of brands associated with both the provided categoryId and subCategoryId",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved brands"),
        @ApiResponse(responseCode = "400", description = "Invalid category or subcategory ID"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getBrandsByCategoryAndSubcategory(
            @Parameter(description = "Category ID", required = true)
            @PathVariable Long categoryId,
            @Parameter(description = "Subcategory ID", required = true)
            @PathVariable Long subCategoryId) {
        
        try {
            if (categoryId == null || categoryId <= 0) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Invalid category ID"));
            }
            
            if (subCategoryId == null || subCategoryId <= 0) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Invalid subcategory ID"));
            }
            
            List<Brand> brands = brandRepository.findByCategoryIdAndSubCategoryId(categoryId, subCategoryId);
            
            List<BrandDataResponse> response = brands.stream()
                .map(brandDataService::convertToResponse)
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error retrieving brands by category ID: {} and subcategory ID: {}", 
                    categoryId, subCategoryId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Error retrieving brands: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 4: Get Specific Brand by ID, Category, and Subcategory
     * Path: /brands/:id/category/:categoryId/subcategory/:subCategoryId
     * Method: GET
     */
    @GetMapping("/{id}/category/{categoryId}/subcategory/{subCategoryId}")
    @Operation(
        summary = "Get specific brand by ID, category, and subcategory",
        description = "Retrieve a specific brand based on its id, categoryId, and subCategoryId",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Brand found"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "404", description = "Brand not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getBrandByIdCategoryAndSubcategory(
            @Parameter(description = "Brand ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Category ID", required = true)
            @PathVariable Long categoryId,
            @Parameter(description = "Subcategory ID", required = true)
            @PathVariable(required = false) Long subCategoryId) {
        
        try {
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Invalid brand ID"));
            }
            
            if (categoryId == null || categoryId <= 0) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Invalid category ID"));
            }
            
            // Allow null subcategory ID
            if (subCategoryId != null && subCategoryId <= 0) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Invalid subcategory ID"));
            }
            
            Optional<Brand> brandOpt;
            
            if (subCategoryId == null) {
                // If subCategoryId is null, find by id and categoryId only
                brandOpt = brandRepository.findById(id)
                    .filter(brand -> categoryId.equals(brand.getCategoryId()) && 
                                    brand.getSubCategoryId() == null);
            } else {
                // If subCategoryId is provided, find by all three criteria
                brandOpt = brandRepository.findByIdAndCategoryIdAndSubCategoryId(id, categoryId, subCategoryId);
            }
            
            if (brandOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("Brand not found with ID: " + id + 
                            ", category ID: " + categoryId + 
                            (subCategoryId != null ? ", subcategory ID: " + subCategoryId : "")));
            }
            
            Brand brand = brandOpt.get();
            
            BrandDataResponse response = brandDataService.convertToResponse(brand);
            
            Map<String, Object> result = new HashMap<>();
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error retrieving brand by ID: {}, category ID: {}, subcategory ID: {}", 
                    id, categoryId, subCategoryId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Error retrieving brand: " + e.getMessage()));
        }
    }
}
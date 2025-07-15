package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.service.BrandCategoryResolutionService;
import com.example.jwtauthenticator.service.BrandCategoryUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/brand-categories")
@RequiredArgsConstructor
@Slf4j
public class BrandCategoryController {

    private final BrandCategoryResolutionService brandCategoryResolutionService;
    private final BrandCategoryUpdateService brandCategoryUpdateService;

    /**
     * Resolve category IDs for a given industry string
     */
    @GetMapping("/resolve")
    public ResponseEntity<BrandCategoryResolutionService.CategoryResolutionResult> resolveCategoryIds(
            @RequestParam String industry) {
        
        BrandCategoryResolutionService.CategoryResolutionResult result = 
                brandCategoryResolutionService.resolveCategoryIds(industry);
        
        return ResponseEntity.ok(result);
    }

    /**
     * Update category IDs for a specific brand
     */
    @PutMapping("/brands/{brandId}/update-categories")
    public ResponseEntity<Map<String, Object>> updateBrandCategoryIds(@PathVariable Long brandId) {
        boolean updated = brandCategoryUpdateService.updateBrandCategoryIds(brandId);
        
        if (updated) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Brand category IDs updated successfully",
                "brandId", brandId
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update category IDs for all brands
     */
    @PutMapping("/brands/update-all-categories")
    public ResponseEntity<Map<String, Object>> updateAllBrandCategoryIds(
            @RequestParam(defaultValue = "100") int batchSize) {
        
        long updatedCount = brandCategoryUpdateService.updateAllBrandCategoryIds(batchSize);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "All brand category IDs updated successfully",
            "updatedCount", updatedCount,
            "batchSize", batchSize
        ));
    }

    /**
     * Update category IDs for brands with a specific industry
     */
    @PutMapping("/brands/update-by-industry")
    public ResponseEntity<Map<String, Object>> updateBrandCategoryIdsByIndustry(
            @RequestParam String industry) {
        
        long updatedCount = brandCategoryUpdateService.updateBrandCategoryIdsByIndustry(industry);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Brand category IDs updated for industry: " + industry,
            "updatedCount", updatedCount,
            "industry", industry
        ));
    }

    /**
     * Get statistics about category ID resolution
     */
    @GetMapping("/stats")
    public ResponseEntity<BrandCategoryUpdateService.CategoryResolutionStats> getCategoryResolutionStats() {
        BrandCategoryUpdateService.CategoryResolutionStats stats = 
                brandCategoryUpdateService.getCategoryResolutionStats();
        
        return ResponseEntity.ok(stats);
    }
}
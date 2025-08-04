package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.Brand;
import com.example.jwtauthenticator.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BrandOptimizedService {
    
    private final BrandRepository brandRepository;
    
    /**
     * Efficiently load all brands with their relationships to avoid N+1 problem
     * Uses batch fetching (@BatchSize) to optimize lazy loading
     */
    public List<Brand> findAllBrandsWithRelations() {
        long startTime = System.currentTimeMillis();
        
        // Get all brands
        List<Brand> brands = brandRepository.findAllWithRelations();
        
        if (!brands.isEmpty()) {
            // Force initialization of all collections within the transaction
            // The @BatchSize annotation will ensure these are loaded efficiently
            brands.forEach(brand -> {
                brand.getAssets().size(); // Initialize assets (batched)
                brand.getColors().size(); // Initialize colors (batched)
                brand.getFonts().size(); // Initialize fonts (batched)
                brand.getSocialLinks().size(); // Initialize social links (batched)
                brand.getImages().size(); // Initialize images (batched)
            });
        }
        
        long endTime = System.currentTimeMillis();
        log.info("Loaded {} brands with all relationships using batch fetching in {}ms", 
                brands.size(), endTime - startTime);
        
        return brands;
    }
    
    /**
     * Paginated version of the optimized brand loading
     */
    public Page<Brand> findAllBrandsWithRelations(Pageable pageable) {
        long startTime = System.currentTimeMillis();
        
        // For paginated queries, we'll use a simpler approach
        // Load the page and initialize collections within the transaction
        Page<Brand> brandPage = brandRepository.findAllWithRelations(pageable);
        List<Brand> brands = brandPage.getContent();
        
        if (!brands.isEmpty()) {
            // Force initialization of collections within the transaction
            brands.forEach(brand -> {
                brand.getAssets().size(); // Initialize assets
                brand.getColors().size(); // Initialize colors  
                brand.getFonts().size(); // Initialize fonts
                brand.getSocialLinks().size(); // Initialize social links
                brand.getImages().size(); // Initialize images
            });
        }
        
        long endTime = System.currentTimeMillis();
        log.info("Loaded {} brands (page {}) with all relationships in {}ms", 
                brands.size(), pageable.getPageNumber(), endTime - startTime);
        
        return brandPage;
    }
    


}
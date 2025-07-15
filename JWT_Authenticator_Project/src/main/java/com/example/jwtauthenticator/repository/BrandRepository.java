package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {
    
    Optional<Brand> findByWebsite(String website);
    
    Optional<Brand> findByNameIgnoreCase(String name);
    
    @Query("SELECT b FROM Brand b WHERE LOWER(b.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Brand> findByNameContainingIgnoreCase(@Param("name") String name);
    
    @Query("SELECT b FROM Brand b WHERE b.website LIKE %:domain%")
    List<Brand> findByWebsiteContaining(@Param("domain") String domain);
    
    // For future automated updates
    @Query("SELECT b FROM Brand b WHERE b.needsUpdate = true ORDER BY b.freshnessScore ASC")
    List<Brand> findBrandsNeedingUpdate(Pageable pageable);
    
    @Query("SELECT b FROM Brand b WHERE b.lastExtractionTimestamp < :cutoffTime")
    List<Brand> findBrandsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    // Analytics queries
    @Query("SELECT COUNT(b) FROM Brand b WHERE b.createdAt >= :startDate")
    Long countBrandsCreatedSince(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT b FROM Brand b ORDER BY b.createdAt DESC")
    Page<Brand> findAllOrderByCreatedAtDesc(Pageable pageable);
    
    // Search functionality
    @Query("SELECT b FROM Brand b WHERE " +
           "LOWER(b.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(b.website) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(b.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(b.industry) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Brand> searchBrands(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    // Count brands that have been claimed
    long countByIsBrandClaimedTrue();
    
    // Methods for category ID statistics and filtering
    List<Brand> findByIndustryIgnoreCase(String industry);
    long countByCategoryIdIsNotNull();
    long countBySubCategoryIdIsNotNull();
    long countByCategoryIdIsNotNullAndSubCategoryIdIsNotNull();
    long countByCategoryIdIsNullAndSubCategoryIdIsNull();
    
    // Methods for the new endpoints
    List<Brand> findByCategoryId(Long categoryId);
    List<Brand> findByCategoryIdAndSubCategoryId(Long categoryId, Long subCategoryId);
    Optional<Brand> findByIdAndCategoryIdAndSubCategoryId(Long id, Long categoryId, Long subCategoryId);
}
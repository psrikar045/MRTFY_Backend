package com.example.jwtauthenticator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.jwtauthenticator.entity.BrandCategory;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandCategoryRepository extends JpaRepository<BrandCategory, Long> {

    // Fetch by unique fields
    Optional<BrandCategory> findByCategoryName(String categoryName);
    Optional<BrandCategory> findByExternalId(String externalId);

    // Check existence by unique fields
    Boolean existsByCategoryName(String categoryName);
    Boolean existsByExternalId(String externalId);

    // Fetch by other fields
    List<BrandCategory> findByIsActive(Boolean isActive);
    
    // Case-insensitive search for category name with active status
    Optional<BrandCategory> findByCategoryNameIgnoreCaseAndIsActive(String categoryName, Boolean isActive);
    
}

package com.example.jwtauthenticator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.jwtauthenticator.entity.BrandSubCategory;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandSubCategoryRepository extends JpaRepository<BrandSubCategory, Long> {

    // Fetch by composite unique key (SubCategoryName and CategoryID)
    Optional<BrandSubCategory> findBySubCategoryNameAndCategoryId(String subCategoryName, Long categoryId);

    // Check existence by composite unique key
    Boolean existsBySubCategoryNameAndCategoryId(String subCategoryName, Long categoryId);

    // Fetch by foreign key (Parent ID)
    List<BrandSubCategory> findByCategoryId(Long categoryId);

    // Fetch by unique external ID
    Optional<BrandSubCategory> findByExternalId(String externalId);

    // Check existence by unique external ID
    Boolean existsByExternalId(String externalId);

    // Fetch by other fields
    List<BrandSubCategory> findByIsActive(Boolean isActive);
}

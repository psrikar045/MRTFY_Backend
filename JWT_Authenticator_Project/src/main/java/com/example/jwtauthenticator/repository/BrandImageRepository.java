package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.BrandImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandImageRepository extends JpaRepository<BrandImage, Long> {
    
    List<BrandImage> findByBrandId(Long brandId);
    
    Optional<BrandImage> findBySourceUrl(String sourceUrl);
    
    @Query("SELECT bi FROM BrandImage bi WHERE bi.downloadStatus = :status")
    List<BrandImage> findByDownloadStatus(@Param("status") BrandImage.DownloadStatus status);
    
    @Query("SELECT bi FROM BrandImage bi WHERE bi.downloadStatus = 'FAILED' AND bi.downloadAttempts < :maxAttempts")
    List<BrandImage> findFailedImagesForRetry(@Param("maxAttempts") Integer maxAttempts);
    
    @Query("SELECT COUNT(bi) FROM BrandImage bi WHERE bi.brand.id = :brandId AND bi.downloadStatus = 'COMPLETED'")
    Long countCompletedImagesByBrandId(@Param("brandId") Long brandId);
}
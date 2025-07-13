package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.BrandAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandAssetRepository extends JpaRepository<BrandAsset, Long> {
    
    List<BrandAsset> findByBrandId(Long brandId);
    
    List<BrandAsset> findByBrandIdAndAssetType(Long brandId, BrandAsset.AssetType assetType);
    
    Optional<BrandAsset> findByOriginalUrl(String originalUrl);
    
    @Query("SELECT ba FROM BrandAsset ba WHERE ba.downloadStatus = :status")
    List<BrandAsset> findByDownloadStatus(@Param("status") BrandAsset.DownloadStatus status);
    
    @Query("SELECT ba FROM BrandAsset ba WHERE ba.downloadStatus = 'FAILED' AND ba.downloadAttempts < :maxAttempts")
    List<BrandAsset> findFailedAssetsForRetry(@Param("maxAttempts") Integer maxAttempts);
    
    @Query("SELECT COUNT(ba) FROM BrandAsset ba WHERE ba.brand.id = :brandId AND ba.downloadStatus = 'COMPLETED'")
    Long countCompletedAssetsByBrandId(@Param("brandId") Long brandId);
}
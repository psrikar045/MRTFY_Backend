package com.example.jwtauthenticator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.jwtauthenticator.entity.Region;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {

    // Fetch by unique fields
    Optional<Region> findByRegionName(String regionName);
    Optional<Region> findByRegionCode(String regionCode);
    Optional<Region> findByExternalId(String externalId);

    // Check existence by unique fields
    Boolean existsByRegionName(String regionName);
    Boolean existsByRegionCode(String regionCode);
    Boolean existsByExternalId(String externalId);

    // Fetch by other fields
    List<Region> findByIsActive(Boolean isActive);
}
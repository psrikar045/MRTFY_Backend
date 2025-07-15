package com.example.jwtauthenticator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.jwtauthenticator.entity.Country;

import java.util.List;
import java.util.Optional;

@Repository
public interface CountryRepository extends JpaRepository<Country, Long> {

    // Fetch by composite unique key (CountryName and RegionID)
    Optional<Country> findByCountryNameAndRegionId(String countryName, Long regionId);

    // Check existence by composite unique key
    Boolean existsByCountryNameAndRegionId(String countryName, Long regionId);

    // Fetch by unique fields
    Optional<Country> findByCountryCode(String countryCode);
    Optional<Country> findByExternalId(String externalId);

    // Check existence by unique fields
    Boolean existsByCountryCode(String countryCode);
    Boolean existsByExternalId(String externalId);

    // Fetch by foreign key (Parent ID)
    List<Country> findByRegionId(Long regionId);

    // Fetch by other fields
    List<Country> findByIsActive(Boolean isActive);
}

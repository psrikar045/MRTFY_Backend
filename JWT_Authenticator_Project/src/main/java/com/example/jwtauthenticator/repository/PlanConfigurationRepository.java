package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.PlanConfiguration;
import com.example.jwtauthenticator.enums.UserPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing plan configurations
 */
@Repository
public interface PlanConfigurationRepository extends JpaRepository<PlanConfiguration, UUID> {
    
    /**
     * Find plan configuration by plan name
     */
    Optional<PlanConfiguration> findByPlanName(UserPlan planName);
    
    /**
     * Find all active plan configurations
     */
    List<PlanConfiguration> findByIsActiveTrueOrderBySortOrder();
    
    /**
     * Find all plan configurations ordered by sort order
     */
    List<PlanConfiguration> findAllByOrderBySortOrder();
    
    /**
     * Check if a plan configuration exists for the given plan
     */
    boolean existsByPlanName(UserPlan planName);
    
    /**
     * Find plans within a price range
     */
    @Query("SELECT p FROM PlanConfiguration p WHERE p.pricePerMonth BETWEEN :minPrice AND :maxPrice AND p.isActive = true ORDER BY p.pricePerMonth")
    List<PlanConfiguration> findByPriceRange(@Param("minPrice") Double minPrice, @Param("maxPrice") Double maxPrice);
    
    /**
     * Find plans that support unlimited features
     */
    @Query("SELECT p FROM PlanConfiguration p WHERE (p.maxDomains = -1 OR p.maxApiKeys = -1 OR p.monthlyApiCalls = -1) AND p.isActive = true")
    List<PlanConfiguration> findUnlimitedPlans();
    
    /**
     * Find the default free plan
     */
    @Query("SELECT p FROM PlanConfiguration p WHERE p.planName = 'FREE' AND p.isActive = true")
    Optional<PlanConfiguration> findFreePlan();
    
    /**
     * Find plans suitable for API key count
     */
    @Query("SELECT p FROM PlanConfiguration p WHERE (p.maxApiKeys >= :requiredKeys OR p.maxApiKeys = -1) AND p.isActive = true ORDER BY p.pricePerMonth")
    List<PlanConfiguration> findPlansForApiKeyCount(@Param("requiredKeys") int requiredKeys);
    
    /**
     * Find plans suitable for domain count
     */
    @Query("SELECT p FROM PlanConfiguration p WHERE (p.maxDomains >= :requiredDomains OR p.maxDomains = -1) AND p.isActive = true ORDER BY p.pricePerMonth")
    List<PlanConfiguration> findPlansForDomainCount(@Param("requiredDomains") int requiredDomains);
    
    /**
     * Find plans suitable for API call volume
     */
    @Query("SELECT p FROM PlanConfiguration p WHERE (p.monthlyApiCalls >= :requiredCalls OR p.monthlyApiCalls = -1) AND p.isActive = true ORDER BY p.pricePerMonth")
    List<PlanConfiguration> findPlansForApiCallVolume(@Param("requiredCalls") int requiredCalls);
}
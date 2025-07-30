package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.ApiKeyAddOn;
import com.example.jwtauthenticator.entity.AddOnPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ApiKeyAddOnRepository extends JpaRepository<ApiKeyAddOn, String> {

    /**
     * Find all active add-ons for an API key
     */
    @Query("SELECT addon FROM ApiKeyAddOn addon WHERE addon.apiKeyId = :apiKeyId " +
           "AND addon.isActive = true AND addon.expiresAt > :currentTime " +
           "AND addon.requestsRemaining > 0 ORDER BY addon.activatedAt ASC")
    List<ApiKeyAddOn> findActiveAddOnsForApiKey(@Param("apiKeyId") String apiKeyId, 
                                               @Param("currentTime") LocalDateTime currentTime);

    /**
     * Find all add-ons for an API key (including expired/inactive)
     */
    List<ApiKeyAddOn> findByApiKeyIdOrderByActivatedAtDesc(String apiKeyId);

    /**
     * Find all add-ons for a user
     */
    List<ApiKeyAddOn> findByUserFkIdOrderByActivatedAtDesc(String userFkId);

    /**
     * Find add-ons by package type
     */
    List<ApiKeyAddOn> findByAddOnPackage(AddOnPackage addOnPackage);

    /**
     * Find expiring add-ons (within next N days)
     */
    @Query("SELECT addon FROM ApiKeyAddOn addon WHERE addon.isActive = true " +
           "AND addon.expiresAt BETWEEN :now AND :expirationThreshold")
    List<ApiKeyAddOn> findExpiringAddOns(@Param("now") LocalDateTime now,
                                        @Param("expirationThreshold") LocalDateTime expirationThreshold);

    /**
     * Find nearly exhausted add-ons (< 10% remaining)
     */
    @Query("SELECT addon FROM ApiKeyAddOn addon WHERE addon.isActive = true " +
           "AND addon.requestsRemaining < (addon.additionalRequests * 0.1)")
    List<ApiKeyAddOn> findNearlyExhaustedAddOns();

    /**
     * Get total additional requests available for an API key
     */
    @Query("SELECT COALESCE(SUM(addon.requestsRemaining), 0) FROM ApiKeyAddOn addon " +
           "WHERE addon.apiKeyId = :apiKeyId AND addon.isActive = true " +
           "AND addon.expiresAt > :currentTime")
    Integer getTotalAdditionalRequestsAvailable(@Param("apiKeyId") String apiKeyId,
                                              @Param("currentTime") LocalDateTime currentTime);

    /**
     * Get monthly spending on add-ons for a user
     */
    @Query("SELECT COALESCE(SUM(addon.monthlyPrice), 0.0) FROM ApiKeyAddOn addon " +
           "WHERE addon.userFkId = :userFkId AND addon.isActive = true " +
           "AND addon.billingCycleStart <= :currentTime AND addon.billingCycleEnd > :currentTime")
    Double getMonthlySpendingForUser(@Param("userFkId") String userFkId,
                                   @Param("currentTime") LocalDateTime currentTime);

    /**
     * Find add-ons that need auto-renewal
     */
    @Query("SELECT addon FROM ApiKeyAddOn addon WHERE addon.autoRenew = true " +
           "AND addon.isActive = true AND addon.expiresAt BETWEEN :now AND :renewalWindow")
    List<ApiKeyAddOn> findAddOnsForAutoRenewal(@Param("now") LocalDateTime now,
                                              @Param("renewalWindow") LocalDateTime renewalWindow);

    /**
     * Clean up expired add-ons
     */
    @Query("UPDATE ApiKeyAddOn addon SET addon.isActive = false " +
           "WHERE addon.expiresAt < :currentTime AND addon.isActive = true")
    int deactivateExpiredAddOns(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Get system-wide add-on statistics
     */
    @Query("SELECT " +
           "COUNT(addon) as totalAddOns, " +
           "COUNT(DISTINCT addon.apiKeyId) as apiKeysWithAddOns, " +
           "SUM(addon.monthlyPrice) as totalMonthlyRevenue, " +
           "AVG(addon.monthlyPrice) as avgAddOnPrice " +
           "FROM ApiKeyAddOn addon WHERE addon.isActive = true")
    Object[] getSystemWideAddOnStats();
}
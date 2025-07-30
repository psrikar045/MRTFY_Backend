package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.AddOnPurchaseRequestDTO;
import com.example.jwtauthenticator.dto.AddOnRecommendationDTO;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.ApiKeyAddOn;
import com.example.jwtauthenticator.entity.AddOnPackage;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.ApiKeyAddOnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing API key add-on packages
 * Professional add-on system with purchase, renewal, and recommendation features
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAddOnService {

    private final ApiKeyAddOnRepository addOnRepository;
    private final ApiKeyRepository apiKeyRepository;

    /**
     * Purchase an add-on package for an API key
     */
    @Transactional
    public ApiKeyAddOn purchaseAddOn(AddOnPurchaseRequestDTO request) {
        // Validate API key exists
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(java.util.UUID.fromString(request.getApiKeyId()));
        if (apiKeyOpt.isEmpty()) {
            throw new IllegalArgumentException("API key not found: " + request.getApiKeyId());
        }

        ApiKey apiKey = apiKeyOpt.get();
        AddOnPackage addOnPackage = request.getAddOnPackage();

        // Handle custom add-on
        int additionalRequests = addOnPackage.getAdditionalRequests();
        double monthlyPrice = addOnPackage.getMonthlyPrice();
        
        if (addOnPackage.isCustom()) {
            additionalRequests = request.getCustomRequests();
            monthlyPrice = request.getCustomPrice();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMonths(request.getDurationMonths());

        ApiKeyAddOn addOn = ApiKeyAddOn.builder()
                .apiKeyId(request.getApiKeyId())
                .userFkId(apiKey.getUserFkId())
                .addOnPackage(addOnPackage)
                .additionalRequests(additionalRequests)
                .monthlyPrice(monthlyPrice)
                .activatedAt(now)
                .expiresAt(expiresAt)
                .isActive(true)
                .requestsUsed(0)
                .requestsRemaining(additionalRequests)
                .autoRenew(request.isAutoRenew())
                .purchaseReason(request.getReason())
                .billingCycleStart(now)
                .billingCycleEnd(now.plusMonths(1))
                .build();

        ApiKeyAddOn savedAddOn = addOnRepository.save(addOn);

        log.info("Add-on purchased: {} for API key {} - {} additional requests for ${}/month", 
                addOnPackage.getDisplayName(), request.getApiKeyId(), additionalRequests, monthlyPrice);

        return savedAddOn;
    }

    /**
     * Get add-on recommendations based on usage patterns
     */
    public AddOnRecommendationDTO getAddOnRecommendations(String apiKeyId, int overageRequests) {
        AddOnPackage recommended = AddOnPackage.getRecommendedAddOn(overageRequests);
        
        // Calculate potential savings with different packages
        List<AddOnPackage> alternatives = List.of(AddOnPackage.values());
        
        return AddOnRecommendationDTO.builder()
                .apiKeyId(apiKeyId)
                .overageRequests(overageRequests)
                .recommendedPackage(recommended)
                .alternativePackages(alternatives)
                .estimatedMonthlySavings(calculateSavings(overageRequests, recommended))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Get all add-ons for an API key
     */
    public List<ApiKeyAddOn> getAddOnsForApiKey(String apiKeyId) {
        return addOnRepository.findByApiKeyIdOrderByActivatedAtDesc(apiKeyId);
    }

    /**
     * Get active add-ons for an API key
     */
    public List<ApiKeyAddOn> getActiveAddOnsForApiKey(String apiKeyId) {
        return addOnRepository.findActiveAddOnsForApiKey(apiKeyId, LocalDateTime.now());
    }

    /**
     * Cancel an add-on (disable auto-renewal)
     */
    @Transactional
    public void cancelAddOn(String addOnId, String reason) {
        Optional<ApiKeyAddOn> addOnOpt = addOnRepository.findById(addOnId);
        if (addOnOpt.isEmpty()) {
            throw new IllegalArgumentException("Add-on not found: " + addOnId);
        }

        ApiKeyAddOn addOn = addOnOpt.get();
        addOn.setAutoRenew(false);
        addOn.setPurchaseReason(addOn.getPurchaseReason() + " | Cancelled: " + reason);
        
        addOnRepository.save(addOn);
        
        log.info("Add-on cancelled: {} - Reason: {}", addOnId, reason);
    }

    /**
     * Renew an add-on for another billing cycle
     */
    @Transactional
    public ApiKeyAddOn renewAddOn(String addOnId, int durationMonths) {
        Optional<ApiKeyAddOn> addOnOpt = addOnRepository.findById(addOnId);
        if (addOnOpt.isEmpty()) {
            throw new IllegalArgumentException("Add-on not found: " + addOnId);
        }

        ApiKeyAddOn addOn = addOnOpt.get();
        LocalDateTime now = LocalDateTime.now();
        
        // Extend expiration
        addOn.setExpiresAt(addOn.getExpiresAt().plusMonths(durationMonths));
        
        // Reset requests if needed
        if (addOn.getRequestsRemaining() <= 0) {
            addOn.setRequestsRemaining(addOn.getAdditionalRequests());
            addOn.setRequestsUsed(0);
            addOn.setIsActive(true);
        }
        
        // Update billing cycle
        addOn.setBillingCycleStart(now);
        addOn.setBillingCycleEnd(now.plusMonths(1));
        
        ApiKeyAddOn renewedAddOn = addOnRepository.save(addOn);
        
        log.info("Add-on renewed: {} for {} months", addOnId, durationMonths);
        
        return renewedAddOn;
    }

    /**
     * Get add-ons that are expiring soon (within 7 days)
     */
    public List<ApiKeyAddOn> getExpiringAddOns() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysFromNow = now.plusDays(7);
        return addOnRepository.findExpiringAddOns(now, sevenDaysFromNow);
    }

    /**
     * Get add-ons that are nearly exhausted (< 10% remaining)
     */
    public List<ApiKeyAddOn> getNearlyExhaustedAddOns() {
        return addOnRepository.findNearlyExhaustedAddOns();
    }

    /**
     * Process auto-renewals for add-ons
     */
    @Transactional
    public void processAutoRenewals() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime renewalWindow = now.plusDays(3); // Renew 3 days before expiration
        
        List<ApiKeyAddOn> addOnsToRenew = addOnRepository.findAddOnsForAutoRenewal(now, renewalWindow);
        
        for (ApiKeyAddOn addOn : addOnsToRenew) {
            try {
                renewAddOn(addOn.getId(), 1); // Renew for 1 month
                log.info("Auto-renewed add-on: {}", addOn.getId());
            } catch (Exception e) {
                log.error("Failed to auto-renew add-on: {}", addOn.getId(), e);
            }
        }
    }

    /**
     * Clean up expired add-ons
     */
    @Transactional
    public int cleanupExpiredAddOns() {
        return addOnRepository.deactivateExpiredAddOns(LocalDateTime.now());
    }

    /**
     * Get monthly spending for a user
     */
    public double getMonthlySpendingForUser(String userFkId) {
        Double spending = addOnRepository.getMonthlySpendingForUser(userFkId, LocalDateTime.now());
        return spending != null ? spending : 0.0;
    }

    /**
     * Calculate potential savings with a recommended package
     */
    private double calculateSavings(int overageRequests, AddOnPackage recommendedPackage) {
        // Simple calculation: compare cost per request
        double costPerRequest = recommendedPackage.getCostPerRequest();
        double overageCost = overageRequests * 0.10; // Assume $0.10 per overage request
        double packageCost = recommendedPackage.getMonthlyPrice();
        
        return Math.max(0, overageCost - packageCost);
    }
}
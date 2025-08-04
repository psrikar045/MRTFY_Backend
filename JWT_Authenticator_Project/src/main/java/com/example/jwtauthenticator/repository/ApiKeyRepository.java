package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * Finds all API keys associated with a specific user, identified by their String 'id'.
     * @param userFkId The String 'id' of the user (from public.users.id).
     * @return A list of ApiKey entities.
     */
    List<ApiKey> findByUserFkId(String userFkId);

    /**
     * Finds a specific API key by its ID and ensures it belongs to a given user,
     * identified by their String 'id'.
     * @param id The UUID of the API key.
     * @param userFkId The String 'id' of the user (from public.users.id).
     * @return An Optional containing the ApiKey if found and belongs to the user, otherwise empty.
     */
    Optional<ApiKey> findByIdAndUserFkId(UUID id, String userFkId);

    Boolean existsByKeyHash(String keyHash);
    
    /**
     * Check if an API key with the given name already exists for a specific user.
     * @param name The name of the API key to check.
     * @param userFkId The String 'id' of the user.
     * @return true if an API key with this name exists for the user, false otherwise.
     */
    Boolean existsByNameAndUserFkId(String name, String userFkId);
    
    // --- NEW DOMAIN-RELATED QUERIES ---
    
    /**
     * Find API key by registered domain
     * @param registeredDomain The registered domain to search for
     * @return Optional containing the ApiKey if found
     */
    Optional<ApiKey> findByRegisteredDomain(String registeredDomain);
    
    /**
     * Check if a registered domain already exists (for uniqueness validation)
     * @param registeredDomain The domain to check
     * @return true if domain is already registered to an API key
     */
    Boolean existsByRegisteredDomain(String registeredDomain);
    
    /**
     * Find all API keys that allow a specific domain (for future hybrid approach)
     * Searches both registered_domain and allowed_domains fields
     * @param domain The domain to search for
     * @return List of API keys that allow this domain
     */
    @Query("SELECT a FROM ApiKey a WHERE " +
           "LOWER(a.registeredDomain) = LOWER(:domain) OR " +
           "LOWER(a.allowedDomains) LIKE LOWER(CONCAT('%', :domain, '%'))")
    List<ApiKey> findByAnyDomain(@Param("domain") String domain);
    
    /**
     * Find API keys by user with domain information (for management UI)
     * @param userFkId The user ID
     * @return List of API keys with domain info
     */
    @Query("SELECT a FROM ApiKey a WHERE a.userFkId = :userFkId ORDER BY a.createdAt DESC")
    List<ApiKey> findByUserFkIdOrderByCreatedAtDesc(@Param("userFkId") String userFkId);
    
    /**
     * Count API keys for a specific user
     * @param userFkId The user ID
     * @return Number of API keys for the user
     */
    int countByUserFkId(String userFkId);
}
package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyValue(String keyValue);

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

    Boolean existsByKeyValue(String keyValue);
}
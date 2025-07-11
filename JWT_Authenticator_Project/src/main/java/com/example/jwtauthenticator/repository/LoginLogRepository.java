package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {
    
    List<LoginLog> findByUserIdOrderByLoginTimeDesc(UUID userId);
    
    List<LoginLog> findByUsernameOrderByLoginTimeDesc(String username);
    
    List<LoginLog> findByLoginStatusOrderByLoginTimeDesc(String loginStatus);
    
    /**
     * Find the most recent login record for a user with a specific login status
     * @param userId The user's UUID
     * @param loginStatus The login status (SUCCESS or FAILURE)
     * @return Optional containing the most recent login record if found
     */
    Optional<LoginLog> findTopByUserIdAndLoginStatusOrderByLoginTimeDesc(UUID userId, String loginStatus);
    
    /**
     * Find the most recent login record for a user with a specific login status using username
     * @param username The username
     * @param loginStatus The login status (SUCCESS or FAILURE)
     * @return Optional containing the most recent login record if found
     */
    Optional<LoginLog> findTopByUsernameAndLoginStatusOrderByLoginTimeDesc(String username, String loginStatus);
}
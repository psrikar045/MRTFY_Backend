package com.example.jwtauthenticator.scheduler;

import com.example.jwtauthenticator.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetCodeCleanupTask {

    private final AuthService authService;

    // Run every hour to clean up expired verification codes
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    public void cleanupExpiredCodes() {
        log.info("Running scheduled task to clean up expired password reset codes");
        authService.cleanupExpiredCodes();
    }
}
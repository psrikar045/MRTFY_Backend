package com.example.jwtauthenticator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetCodeCleanupScheduler {

    private final AuthService authService;

    @Scheduled(fixedRate = 300000) // every 5 minutes
    public void cleanupExpiredCodes() {
        authService.cleanupExpiredCodes();
    }
}

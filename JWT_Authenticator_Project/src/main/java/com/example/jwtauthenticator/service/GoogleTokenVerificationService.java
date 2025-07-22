package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.GoogleUserInfo;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class GoogleTokenVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleTokenVerificationService.class);

    @Value("${google.oauth2.client-id:}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void init() {
        if (googleClientId == null || googleClientId.trim().isEmpty()) {
            logger.warn("Google OAuth2 client ID not configured. Google Sign-In will not work.");
            return;
        }

        verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
        
        logger.info("Google Token Verifier initialized with client ID: {}", 
                    googleClientId.substring(0, Math.min(10, googleClientId.length())) + "...");
    }

    public GoogleUserInfo verifyToken(String idTokenString) throws GeneralSecurityException, IOException {
        if (verifier == null) {
            throw new IllegalStateException("Google OAuth2 client ID not configured");
        }

        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken != null) {
            GoogleIdToken.Payload payload = idToken.getPayload();

            return GoogleUserInfo.builder()
                    .googleId(payload.getSubject())
                    .email((String) payload.get("email"))
                    .emailVerified((Boolean) payload.get("email_verified"))
                    .name((String) payload.get("name"))
                    .givenName((String) payload.get("given_name"))
                    .familyName((String) payload.get("family_name"))
                    .picture((String) payload.get("picture"))
                    .build();
        } else {
            throw new SecurityException("Invalid Google ID token");
        }
    }

    /**
     * Validates if the token has a valid JWT format
     * @param token The token to validate
     * @return true if the token has valid JWT format, false otherwise
     */
    public boolean isValidTokenFormat(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        
        // JWT tokens have 3 parts separated by dots
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        
        // Each part should not be empty
        for (String part : parts) {
            if (part.trim().isEmpty()) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Checks if Google verification is enabled
     * @return true if Google verification is enabled, false otherwise
     */
    public boolean isGoogleVerificationEnabled(String clientId) {
        return verifier != null && clientId != null && !clientId.trim().isEmpty();
    }

    /**
     * Gets Google configuration information
     * @return Map containing Google configuration details
     */
    public java.util.Map<String, Object> getGoogleConfiguration(String clientId) {
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("clientId", clientId);
        config.put("verificationEnabled", isGoogleVerificationEnabled(clientId));
        config.put("verifierInitialized", verifier != null);
        return config;
    }
}
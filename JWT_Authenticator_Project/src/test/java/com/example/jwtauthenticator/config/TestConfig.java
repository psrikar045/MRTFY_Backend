package com.example.jwtauthenticator.config;

import com.example.jwtauthenticator.service.IdGeneratorService;
import com.example.jwtauthenticator.service.EmailService;
import com.example.jwtauthenticator.service.RateLimiterService;
import com.example.jwtauthenticator.service.GoogleTokenVerificationService;
import com.example.jwtauthenticator.util.JwtUtil;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.boot.test.mock.mockito.MockBean;

import freemarker.template.Configuration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@TestConfiguration
@Profile("test")
public class TestConfig {

    // ==================== MOCK BEANS ====================
    
    @MockBean
    private JavaMailSender javaMailSender;
    
    @MockBean
    private Configuration freemarkerConfig;
    
    // ==================== SERVICE MOCKS ====================
    
    @Bean
    @Primary
    public IdGeneratorService idGeneratorService() {
        IdGeneratorService mockService = Mockito.mock(IdGeneratorService.class);
        // Configure the mock to return a predictable ID
        when(mockService.generateDombrUserId()).thenReturn("DOMBR000001");
        when(mockService.generateSimpleDombrUserId()).thenReturn("DOMBR000001");
        when(mockService.generateNextId()).thenReturn("MRTFY0001");
        return mockService;
    }
    
    @Bean
    @Primary
    public AppConfig appConfig() {
        AppConfig mockConfig = Mockito.mock(AppConfig.class);
        when(mockConfig.getApiUrl(anyString())).thenReturn("http://localhost:8080/api/auth/verify-email?token=test-token");
        return mockConfig;
    }
    
    // ==================== TEST BEANS ====================
    
    @Bean
    @Primary
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    @Primary
    public JwtUtil jwtUtil() {
        return new JwtUtil();
    }
    
    // ==================== TEST PROPERTIES ====================
    
    @Bean
    public TestProperties testProperties() {
        return new TestProperties();
    }
    
    /**
     * Configuration properties for testing
     */
    public static class TestProperties {
        private String jwtSecret = "mySecretKeyThatIsAtLeast256BitsLongForJwtTokenGeneration12345";
        private int jwtExpirationMs = 86400000; // 24 hours
        private int refreshTokenExpirationMs = 604800000; // 7 days
        private String fromEmail = "test@example.com";
        private String fromName = "Test Application";
        private String frontendUrl = "http://localhost:3000";
        private String googleClientId = "test-client-id.apps.googleusercontent.com";
        
        // Getters and setters
        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
        
        public int getJwtExpirationMs() { return jwtExpirationMs; }
        public void setJwtExpirationMs(int jwtExpirationMs) { this.jwtExpirationMs = jwtExpirationMs; }
        
        public int getRefreshTokenExpirationMs() { return refreshTokenExpirationMs; }
        public void setRefreshTokenExpirationMs(int refreshTokenExpirationMs) { this.refreshTokenExpirationMs = refreshTokenExpirationMs; }
        
        public String getFromEmail() { return fromEmail; }
        public void setFromEmail(String fromEmail) { this.fromEmail = fromEmail; }
        
        public String getFromName() { return fromName; }
        public void setFromName(String fromName) { this.fromName = fromName; }
        
        public String getFrontendUrl() { return frontendUrl; }
        public void setFrontendUrl(String frontendUrl) { this.frontendUrl = frontendUrl; }
        
        public String getGoogleClientId() { return googleClientId; }
        public void setGoogleClientId(String googleClientId) { this.googleClientId = googleClientId; }
    }
}
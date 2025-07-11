package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.config.AppConfig;
import com.example.jwtauthenticator.dto.*;
import com.example.jwtauthenticator.dto.RegisterResponse;
import com.example.jwtauthenticator.entity.LoginLog;
import com.example.jwtauthenticator.entity.PasswordResetCode;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.entity.User.AuthProvider;
import com.example.jwtauthenticator.entity.User.Role;
import com.example.jwtauthenticator.model.AuthRequest;
import com.example.jwtauthenticator.model.AuthResponse;
import com.example.jwtauthenticator.model.RegisterRequest;
import com.example.jwtauthenticator.repository.LoginLogRepository;
import com.example.jwtauthenticator.repository.PasswordResetCodeRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.security.JwtUserDetailsService;
import com.example.jwtauthenticator.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import jakarta.transaction.Transactional;

@Service
@Slf4j
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private GoogleTokenVerificationService googleTokenVerificationService;

    @Autowired
    private LoginLogRepository loginLogRepository;

    @Autowired
    private PasswordResetCodeRepository passwordResetCodeRepository;

    @Autowired
    private IdGeneratorService idGeneratorService;

    @Transactional
    public RegisterResponse registerUser(RegisterRequest request) {
        // Generate the sequential user ID
        String dombrId;
        try {
            // Try to use the database sequence
            dombrId = idGeneratorService.generateDombrUserId();
            log.info("Generated sequential user ID: {}", dombrId);
        } catch (Exception e) {
            // If that fails, use the simple method
            log.error("Failed to generate ID using sequence: {}", e.getMessage());
            dombrId = idGeneratorService.generateSimpleDombrUserId();
            log.info("Generated simple sequential user ID: {}", dombrId);
        }
        
        // If we still don't have an ID, use a hardcoded one as last resort
        if (dombrId == null) {
            dombrId = "DOMBR000001";
            log.warn("Using hardcoded ID as last resort: {}", dombrId);
        }
        // Auto-generate brandId if not provided
        String brandId = request.brandId();
        if (brandId == null || brandId.trim().isEmpty()) {
            try {
                brandId = idGeneratorService.generateNextId(); // Uses default prefix from application.properties
            } catch (Exception e) {
                log.error("Error generating brand ID, using default", e);
                brandId = "MRTFY" + String.format("%04d", System.currentTimeMillis() % 10000);
            }
        }
        
        if (userRepository.existsByUsernameAndBrandId(request.username(), brandId)) {
            throw new RuntimeException("Username already exists for this brand");
        }
        if (userRepository.existsByEmailAndBrandId(request.email(), brandId)) {
            throw new RuntimeException("Email already exists for this brand");
        }
        
        User newUser = User.builder()
                .id(dombrId) // Set as primary key
                .username(request.username())
                .password(request.password())
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phoneNumber(request.phoneNumber())
                .location(request.location())
                .role(Role.USER) // Default role
                .brandId(brandId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .emailVerified(false)
                .build();

        String verificationToken = UUID.randomUUID().toString();
        newUser.setVerificationToken(verificationToken);

        userDetailsService.save(newUser);

        String verificationLink = appConfig.getApiUrl("/auth/verify-email?token=" + verificationToken);
        emailService.sendEmail(newUser.getEmail(), "Email Verification", "Please click the link to verify your email: " + verificationLink);

        return new RegisterResponse(
            "User registered successfully. Please verify your email.",
            brandId,
            newUser.getUsername(),
            newUser.getEmail(),
            true
        );
    }

    public AuthResponse createAuthenticationToken(AuthRequest authenticationRequest) throws Exception {
        authenticate(authenticationRequest.username(), authenticationRequest.password(), authenticationRequest.brandId());
        final UserDetails userDetails = userDetailsService
                .loadUserByUsernameAndBrandId(authenticationRequest.username(), authenticationRequest.brandId());
        User user = userRepository.findByUsernameAndBrandId(authenticationRequest.username(), authenticationRequest.brandId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Email not verified. Please verify your email to login.");
        }

        final String token = jwtUtil.generateToken(userDetails, user.getUserId().toString());
        final String refreshToken = jwtUtil.generateRefreshToken(userDetails, user.getUserId().toString());

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        // Log successful password login
        logLogin(user, "PASSWORD", "SUCCESS", "Password login successful");

        return new AuthResponse(token, refreshToken, user.getBrandId(), jwtUtil.getAccessTokenExpirationTimeInSeconds());
    }

    public AuthResponse loginUser(AuthRequest authenticationRequest) throws Exception {
        return createAuthenticationToken(authenticationRequest);
    }
    
    public AuthResponse loginWithUsername(String username, String password) throws Exception {
        // Find user by username (across all brands)
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));
        
        // Authenticate with the found user's brandId
        return authenticateAndGenerateToken(username, password, user.getBrandId());
    }
    
    public AuthResponse loginWithEmail(String email, String password) throws Exception {
        // Find user by email (across all brands)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        
        // Authenticate with the found user's brandId
        return authenticateAndGenerateToken(user.getUsername(), password, user.getBrandId());
    }
    
    private AuthResponse authenticateAndGenerateToken(String username, String password, String brandId) throws Exception {
        authenticate(username, password, brandId);
        
        final UserDetails userDetails = userDetailsService.loadUserByUsernameAndBrandId(username, brandId);
        User user = userRepository.findByUsernameAndBrandId(username, brandId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Email not verified. Please verify your email to login.");
        }

        final String token = jwtUtil.generateToken(userDetails, user.getUserId().toString());
        final String refreshToken = jwtUtil.generateRefreshToken(userDetails, user.getUserId().toString());

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        // Log successful password login
        logLogin(user, "PASSWORD", "SUCCESS", "Password login successful");

        return new AuthResponse(token, refreshToken, user.getBrandId(), jwtUtil.getAccessTokenExpirationTimeInSeconds());
    }

    public AuthResponse refreshToken(String oldRefreshToken) throws Exception {
        String username = jwtUtil.extractUsername(oldRefreshToken);
        String userId = jwtUtil.extractUserId(oldRefreshToken);
        // For multi-tenancy, we need to extract tenantId from the refresh token or pass it separately.
        // For simplicity, assuming tenantId is part of the JWT claims or derived from userId.
        // In a real-world scenario, you might store tenantId in the refresh token claims.
        User user = userRepository.findByUserId(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getRefreshToken().equals(oldRefreshToken) || jwtUtil.isTokenExpired(oldRefreshToken)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsernameAndBrandId(username, user.getBrandId());
        final String newToken = jwtUtil.generateToken(userDetails, user.getUserId().toString());
        final String newRefreshToken = jwtUtil.generateRefreshToken(userDetails, user.getUserId().toString());

        user.setRefreshToken(newRefreshToken);
        userRepository.save(user);

        return new AuthResponse(newToken, newRefreshToken, user.getBrandId(), jwtUtil.getAccessTokenExpirationTimeInSeconds());
    }

    public String verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));

        user.setEmailVerified(true);
        user.setVerificationToken(null); // Clear the token after verification
        userRepository.save(user);
        return "Email verified successfully!";
    }

    private void authenticate(String username, String password, String brandId) throws Exception {
        try {
            // Manual authentication for multi-brand setup
            User user = userRepository.findByUsernameAndBrandId(username, brandId)
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
            
            if (!passwordEncoder.matches(password, user.getPassword())) {
                throw new BadCredentialsException("Invalid credentials");
            }
            
            // Additional checks can be added here (e.g., account enabled, not locked, etc.)
            
        } catch (BadCredentialsException e) {
            throw new Exception("INVALID_CREDENTIALS", e);
        }
    }

    public AuthResponse googleSignIn(GoogleSignInRequest request) throws Exception {
        try {
            // Verify Google ID token and extract user info
            GoogleUserInfo googleUserInfo = googleTokenVerificationService.verifyToken(request.idToken());
            
            // Check if user already exists
            Optional<User> existingUserOpt = userRepository.findByEmail(googleUserInfo.getEmail());
            User existingUser = existingUserOpt.orElse(null);
            
            if (existingUser != null) {
                // User exists - update profile picture and email verification if needed
                boolean needsUpdate = false;
                
                if (googleUserInfo.getPicture() != null && 
                    !googleUserInfo.getPicture().equals(existingUser.getProfilePictureUrl())) {
                    existingUser.setProfilePictureUrl(googleUserInfo.getPicture());
                    needsUpdate = true;
                }
                
                // Ensure email is verified for Google users
                if (!existingUser.isEmailVerified()) {
                    existingUser.setEmailVerified(true);
                    needsUpdate = true;
                }
                
                if (needsUpdate) {
                    userRepository.save(existingUser);
                }
                
                // Log successful Google login for existing user
                logLogin(existingUser, "GOOGLE", "SUCCESS", "Google Sign-In successful for existing user");
                
                // Generate JWT token for existing user
                return generateJwtResponse(existingUser);
            } else {
                // User doesn't exist - create new user
                User newUser = createGoogleUser(googleUserInfo);
                userRepository.save(newUser);
                
                // Log successful Google registration and login for new user
                logLogin(newUser, "GOOGLE", "SUCCESS", "Google Sign-In successful - new user created");
                
                // Generate JWT token for new user
                return generateJwtResponse(newUser);
            }
            
        } catch (Exception e) {
            throw new Exception("Google Sign-In failed: " + e.getMessage(), e);
        }
    }

    private User createGoogleUser(GoogleUserInfo googleUserInfo) {
        // Generate a unique username from email
        String baseUsername = googleUserInfo.getEmail().split("@")[0];
        String username = generateUniqueUsername(baseUsername);
        
        // Extract first and last name from Google's name field
        String firstName = null;
        String lastName = null;
        if (googleUserInfo.getName() != null && !googleUserInfo.getName().trim().isEmpty()) {
            String[] nameParts = googleUserInfo.getName().trim().split("\\s+");
            firstName = nameParts[0];
            if (nameParts.length > 1) {
                lastName = String.join(" ", java.util.Arrays.copyOfRange(nameParts, 1, nameParts.length));
            }
        }
        
        // Generate the sequential user ID
        String dombrId;
        try {
            // Try to use the database sequence
            dombrId = idGeneratorService.generateDombrUserId();
            log.info("Generated sequential user ID for Google user: {}", dombrId);
        } catch (Exception e) {
            // If that fails, use the simple method
            log.error("Failed to generate ID using sequence for Google user: {}", e.getMessage());
            dombrId = idGeneratorService.generateSimpleDombrUserId();
            log.info("Generated simple sequential user ID for Google user: {}", dombrId);
        }
        
        // If we still don't have an ID, use a hardcoded one as last resort
        if (dombrId == null) {
            dombrId = "DOMBR" + String.format("%06d", System.currentTimeMillis() % 1000000);
            log.warn("Using timestamp-based ID as last resort for Google user: {}", dombrId);
        }
        
        return User.builder()
                .id(dombrId) // Set the DOMBR ID as primary key
                .username(username)
                .email(googleUserInfo.getEmail())
                .firstName(firstName)
                .lastName(lastName)
                .password(passwordEncoder.encode(UUID.randomUUID().toString())) // Random password for Google users
                .role(Role.USER)
                .authProvider(AuthProvider.GOOGLE)
                .emailVerified(true) // Always true for Google users since Google has already verified the email
                .profilePictureUrl(googleUserInfo.getPicture())
                .brandId("default") // You can modify this based on your brand logic
                .userId(UUID.randomUUID()) // Set UUID
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String generateUniqueUsername(String baseUsername) {
        String username = baseUsername;
        int counter = 1;
        
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter;
            counter++;
        }
        
        return username;
    }

    private AuthResponse generateJwtResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsernameAndBrandId(user.getUsername(), user.getBrandId());
        String accessToken = jwtUtil.generateToken(userDetails, user.getUserId().toString());
        String refreshToken = jwtUtil.generateRefreshToken(userDetails, user.getUserId().toString());
        
        // Save refresh token
        user.setRefreshToken(refreshToken);
        userRepository.save(user);
        
        return new AuthResponse(accessToken, refreshToken, user.getBrandId(), jwtUtil.getAccessTokenExpirationTimeInSeconds());
    }

    private void logLogin(User user, String loginMethod, String loginStatus, String details) {
        try {
            LoginLog loginLog = LoginLog.builder()
                    .userId(user.getUserId())
                    .username(user.getUsername())
                    .loginMethod(loginMethod)
                    .loginStatus(loginStatus)
                    .details(details)
                    .build();
            
            loginLogRepository.save(loginLog);
            System.out.println("LOGIN_LOG: " + user.getUsername() + " - " + loginMethod + " - " + loginStatus);
        } catch (Exception e) {
            System.err.println("Failed to log login event: " + e.getMessage());
        }
    }

    // Profile Update Method
    @Transactional
    public String updateProfile(String username, String brandId, ProfileUpdateRequest request) {
        User user = userRepository.findByUsernameAndBrandId(username, brandId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Update fields if provided
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }
        if (request.location() != null) {
            user.setLocation(request.location());
        }
        if (request.email() != null && !request.email().equals(user.getEmail())) {
            // Check if new email already exists
            if (userRepository.existsByEmailAndBrandId(request.email(), brandId)) {
                throw new RuntimeException("Email already exists for this brand");
            }
            user.setEmail(request.email());
            user.setEmailVerified(false); // Need to verify new email
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return "Profile updated successfully";
    }

    // Check Email Existence Method
    public boolean checkEmailExists(CheckEmailRequest request) {
        return userRepository.existsByEmailAndBrandId(request.email(), request.brandId());
    }

    // Enhanced Forgot Password with Verification Code
    @Transactional
    public String sendPasswordResetCode(ForgotPasswordRequest request) {
        if (!userRepository.existsByEmailAndBrandId(request.email(), request.brandId())) {
            throw new RuntimeException("No account found with this email address");
        }

        passwordResetCodeRepository.deleteByEmailAndBrandId(request.email(), request.brandId());

        String code = generateVerificationCode();

        PasswordResetCode resetCode = PasswordResetCode.builder()
                .email(request.email())
                .brandId(request.brandId())
                .code(code)
                .build();

        passwordResetCodeRepository.save(resetCode);

        emailService.sendPasswordResetCodeEmail(request.email(), code);

        return "Verification code sent to your email address";
    }

    // Forgot password using userId and email
    @Transactional
    public String sendPasswordResetCode(ForgotPasswordCodeRequest request) {
        UUID uuid;
        try {
            uuid = UUID.fromString(request.userId());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid user ID format");
        }

        Optional<User> userOpt = userRepository.findByUserId(uuid);
        if (userOpt.isEmpty() || !userOpt.get().getEmail().equals(request.email())) {
            // Generic response if user not found or email mismatch
            return "If the user ID and email are registered, a verification code will be sent.";
        }

        passwordResetCodeRepository.deleteByEmailAndBrandId(request.email(), request.userId());

        String code = generateVerificationCode();

        PasswordResetCode resetCode = PasswordResetCode.builder()
                .email(request.email())
                .brandId(request.userId())
                .code(code)
                .build();

        passwordResetCodeRepository.save(resetCode);

        emailService.sendPasswordResetCodeEmail(request.email(), code);

        return "Verification code sent successfully.";
    }

    // Verify Reset Code Method - Only verify, don't mark as used yet
    public String verifyResetCode(VerifyCodeRequest request) {
        Optional<PasswordResetCode> resetCodeOpt = passwordResetCodeRepository
                .findByEmailAndBrandIdAndCodeAndUsedFalse(request.email(), request.brandId(), request.code());

        if (resetCodeOpt.isEmpty()) {
            throw new RuntimeException("Invalid verification code");
        }

        PasswordResetCode resetCode = resetCodeOpt.get();
        if (resetCode.isExpired()) {
            throw new RuntimeException("Verification code has expired");
        }

        return "Verification code is valid. You can now proceed to reset your password.";
    }

    // Verify code using userId and email - marks the code as used
    @Transactional
    public String verifyResetCode(VerifyCodeWithUserRequest request) {
        Optional<PasswordResetCode> resetCodeOpt = passwordResetCodeRepository
                .findByEmailAndBrandIdAndCodeAndUsedFalse(request.email(), request.userId(), request.code());

        if (resetCodeOpt.isEmpty()) {
            return "Invalid verification code.";
        }

        PasswordResetCode resetCode = resetCodeOpt.get();
        if (resetCode.isExpired()) {
            passwordResetCodeRepository.delete(resetCode);
            return "Verification code has expired.";
        }

        resetCode.setUsed(true);
        passwordResetCodeRepository.save(resetCode);

        return "Code verified successfully. Proceed to set a new password.";
    }

    // Set New Password Method - Only works after code verification
    @Transactional
    public String setNewPassword(SetNewPasswordRequest request) {
        // Verify the code again before allowing password reset
        Optional<PasswordResetCode> resetCodeOpt = passwordResetCodeRepository
                .findByEmailAndBrandIdAndCodeAndUsedFalse(request.email(), request.brandId(), request.code());

        if (resetCodeOpt.isEmpty()) {
            throw new RuntimeException("Invalid verification code or code already used");
        }

        PasswordResetCode resetCode = resetCodeOpt.get();
        if (resetCode.isExpired()) {
            throw new RuntimeException("Verification code has expired. Please request a new one.");
        }

        // Find and update user password
        User user = userRepository.findByEmailAndBrandId(request.email(), request.brandId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Mark the code as used - now it can't be used again
        resetCode.setUsed(true);
        passwordResetCodeRepository.save(resetCode);

        return "Password has been reset successfully";
    }

    // Helper method to generate 6-digit verification code
    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000); // Generates 6-digit number
        return String.valueOf(code);
    }

    // Cleanup expired codes (can be called by a scheduled task)
    @Transactional
    public void cleanupExpiredCodes() {
        passwordResetCodeRepository.deleteExpiredCodes(LocalDateTime.now());
    }
}



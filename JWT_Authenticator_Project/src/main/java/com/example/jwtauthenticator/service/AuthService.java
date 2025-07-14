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
import java.util.Map;
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
        // First check if username already exists across all brands
        if (userRepository.existsByUsername(request.username())) {
            throw new RuntimeException("Username already exists. Please choose a different username.");
        }
        
        // Then check if email already exists across all brands
        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email already exists. Please use a different email address.");
        }
        
        // Auto-generate brandId if not provided
        String brandId = request.brandId();
        if (brandId == null || brandId.trim().isEmpty()) {
            try {
                brandId = idGeneratorService.generateNextId(); // Uses default prefix from application.properties
            } catch (Exception e) {
                log.error("Error generating brand ID, using default", e);
                brandId = "MRTFY" + String.format("%06d", System.currentTimeMillis() % 10000);
            }
        }
        
        // Additional brand-specific checks (these are redundant now but kept for safety)
        if (userRepository.existsByUsernameAndBrandId(request.username(), brandId)) {
            throw new RuntimeException("Username already exists for this brand");
        }
        if (userRepository.existsByEmailAndBrandId(request.email(), brandId)) {
            throw new RuntimeException("Email already exists for this brand");
        }
        
        User newUser = User.builder()
                .id(brandId) // Set as primary key
                .username(request.username())
                .password(passwordEncoder.encode(request.password())) // Ensure password is encoded
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phoneNumber(request.phoneNumber())
                .location(request.location())
                .role(Role.USER) // Default role
                .brandId("default")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .emailVerified(false)
                .build();

        String verificationToken = UUID.randomUUID().toString();
        newUser.setVerificationToken(verificationToken);

        userDetailsService.save(newUser);

        String baseUrl = appConfig.getApiUrl("");
        emailService.sendVerificationEmail(newUser.getEmail(), newUser.getUsername(), verificationToken, baseUrl);

        return new RegisterResponse(
            "User registered successfully. Please verify your email.",
            brandId,
            newUser.getUsername(),
            newUser.getEmail(),
            true
        );
    }

    public AuthResponse createAuthenticationToken(AuthRequest authenticationRequest) throws Exception {
        try {
            authenticate(authenticationRequest.username(), authenticationRequest.password(), authenticationRequest.brandId());
            final UserDetails userDetails = userDetailsService
                    .loadUserByUsernameAndBrandId(authenticationRequest.username(), authenticationRequest.brandId());
            User user = userRepository.findByUsernameAndBrandId(authenticationRequest.username(), authenticationRequest.brandId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
    
            if (!user.isEmailVerified()) {
                // Log failure due to unverified email
                logLogin(user, "PASSWORD", "FAILURE", "Login failed: Email not verified");
                throw new RuntimeException("Email not verified. Please verify your email to login.");
            }
    
            final String token = jwtUtil.generateToken(userDetails, user.getUserId().toString());
            final String refreshToken = jwtUtil.generateRefreshToken(userDetails, user.getUserId().toString());
    
            user.setRefreshToken(refreshToken);
            userRepository.save(user);
    
            // Log successful password login
            logLogin(user, "PASSWORD", "SUCCESS", "Password login successful");
    
            return new AuthResponse(token, refreshToken, user.getBrandId(), jwtUtil.getAccessTokenExpirationTimeInSeconds());
        } catch (Exception e) {
            // If the exception wasn't already logged in the authenticate method, log it here
            if (!(e.getCause() instanceof BadCredentialsException)) {
                // Create a temporary user object just for logging if needed
                User tempUser = new User();
                tempUser.setUsername(authenticationRequest.username());
                tempUser.setBrandId(authenticationRequest.brandId());
                tempUser.setUserId(UUID.randomUUID()); // Generate a temporary UUID
                logLogin(tempUser, "PASSWORD", "FAILURE", "Authentication failed: " + e.getMessage());
            }
            throw e;
        }
    }

    public AuthResponse loginUser(AuthRequest authenticationRequest) throws Exception {
        return createAuthenticationToken(authenticationRequest);
    }
    
    public AuthResponse loginUser(String usernameOrEmail, String password) throws Exception {
        log.info("Login attempt with identifier: {}", usernameOrEmail); 
        try {
            // Check if the input is an email (contains @ symbol)
            if (usernameOrEmail.contains("@")) {
                log.debug("Detected email format, attempting email-based login");
                // Find user by email
                User user = userRepository.findByEmail(usernameOrEmail)
                        .orElseThrow(() -> new RuntimeException("Invalid email or password"));
                
                log.info("Email found, proceeding with authentication for user: {}", user.getUsername());
                // Authenticate with the found user's username and brandId
                return authenticateAndGenerateToken(user.getUsername(), password, user.getBrandId());
            } else {
                log.debug("Username format detected, attempting username-based login");
                // Find user by username across all brands
                User user = userRepository.findByUsername(usernameOrEmail)
                        .orElseThrow(() -> new RuntimeException("Invalid username or password"));
                
                log.info("Username found, proceeding with authentication with brand ID: {}", user.getBrandId());
                // Authenticate with the found user's brandId
                return authenticateAndGenerateToken(usernameOrEmail, password, user.getBrandId());
            }
        } catch (Exception e) {
            log.error("Login failed for identifier: {}, reason: {}", usernameOrEmail, e.getMessage());
            
            // Create a temporary user object for logging if needed
            User tempUser = new User();
            tempUser.setUsername(usernameOrEmail);
            if (usernameOrEmail.contains("@")) {
                tempUser.setEmail(usernameOrEmail);
            }
            tempUser.setBrandId("unknown");
            tempUser.setUserId(UUID.randomUUID()); // Generate a temporary UUID
            
            logLogin(tempUser, "PASSWORD", "FAILURE", "Login failed: " + e.getMessage());
            throw e;
        }
    }
    
    public AuthResponse loginWithUsername(String username, String password) throws Exception {
        try {
            // Find user by username (across all brands)
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Invalid username or password"));
            
            // Authenticate with the found user's brandId
            return authenticateAndGenerateToken(username, password, user.getBrandId());
        } catch (Exception e) {
            // Log failed login attempt
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                logLogin(userOpt.get(), "PASSWORD", "FAILURE", "Username login failed: " + e.getMessage());
            } else {
                // User not found, create a temporary user object just for logging
                User tempUser = new User();
                tempUser.setUsername(username);
                tempUser.setUserId(UUID.randomUUID()); // Generate a temporary UUID
                logLogin(tempUser, "PASSWORD", "FAILURE", "User not found with provided username");
            }
            throw e;
        }
    }
    
    public AuthResponse loginWithEmail(String email, String password) throws Exception {
        try {
            // Find user by email (across all brands)
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Invalid email or password"));
            
            // Authenticate with the found user's brandId
            return authenticateAndGenerateToken(user.getUsername(), password, user.getBrandId());
        } catch (Exception e) {
            // Log failed login attempt
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                logLogin(userOpt.get(), "PASSWORD", "FAILURE", "Email login failed: " + e.getMessage());
            } else {
                // User not found, create a temporary user object just for logging
                User tempUser = new User();
                tempUser.setEmail(email);
                tempUser.setUsername(email.split("@")[0]); // Use part before @ as username
                tempUser.setUserId(UUID.randomUUID()); // Generate a temporary UUID
                logLogin(tempUser, "PASSWORD", "FAILURE", "User not found with provided email");
            }
            throw e;
        }
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
                // Log failed login attempt due to wrong password
                logLogin(user, "PASSWORD", "FAILURE", "Invalid password provided");
                throw new BadCredentialsException("Invalid credentials");
            }
            
            // Additional checks can be added here (e.g., account enabled, not locked, etc.)
            
        } catch (BadCredentialsException e) {
            // Try to find user just for logging purposes
            Optional<User> userOpt = userRepository.findByUsernameAndBrandId(username, brandId);
            if (userOpt.isPresent()) {
                // We found the user, so log the failure
                logLogin(userOpt.get(), "PASSWORD", "FAILURE", "Authentication failed: " + e.getMessage());
            } else {
                // User not found, create a temporary user object just for logging
                User tempUser = new User();
                tempUser.setUsername(username);
                tempUser.setBrandId(brandId);
                tempUser.setUserId(UUID.randomUUID()); // Generate a temporary UUID
                logLogin(tempUser, "PASSWORD", "FAILURE", "User not found with provided credentials");
            }
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
            // Create a temporary user for logging the failure
            // Since we don't have the email from the request directly, we'll just log with a generic user
            User tempUser = new User();
            tempUser.setUsername("google_signin_attempt");
            tempUser.setUserId(UUID.randomUUID()); // Generate a temporary UUID
            logLogin(tempUser, "GOOGLE", "FAILURE", "Google Sign-In failed: " + e.getMessage());
            
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
        String brandId;
         try {
                brandId = idGeneratorService.generateNextId(); // Uses default prefix from application.properties
            } catch (Exception e) {
                log.error("Error generating brand ID, using default", e);
                brandId = "MRTFY" + String.format("%06d", System.currentTimeMillis() % 10000);
            }
        return User.builder()
                .id(brandId) // Set the DOMBR ID as primary key
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

    /**
     * Logs a login event, maintaining only the most recent success and failure records for each user.
     * For each user, the system keeps only two records:
     * 1. The most recent successful login
     * 2. The most recent failed login
     * 
     * @param user The user who attempted to log in
     * @param loginMethod The login method used (PASSWORD, GOOGLE, etc.)
     * @param loginStatus The login status (SUCCESS or FAILURE)
     * @param details Additional details about the login attempt
     */
    private void logLogin(User user, String loginMethod, String loginStatus, String details) {
        try {
            if (user == null || user.getUserId() == null) {
                log.warn("Cannot log login event: User or user ID is null");
                return;
            }
            
            // Check if there's an existing record for this user with the same login status
            Optional<LoginLog> existingLogOpt = loginLogRepository
                .findTopByUserIdAndLoginStatusOrderByLoginTimeDesc(user.getUserId(), loginStatus);
            
            LoginLog loginLog;
            
            if (existingLogOpt.isPresent()) {
                // Update the existing record with new timestamp and details
                loginLog = existingLogOpt.get();
                loginLog.setLoginTime(LocalDateTime.now());
                loginLog.setLoginMethod(loginMethod);
                loginLog.setDetails(details);
            } else {
                // Create a new record
                loginLog = LoginLog.builder()
                        .userId(user.getUserId())
                        .username(user.getUsername())
                        .loginMethod(loginMethod)
                        .loginStatus(loginStatus)
                        .details(details)
                        .build();
            }
            
            // Save the record
            loginLogRepository.save(loginLog);
            log.info("LOGIN_LOG: {} - {} - {}", user.getUsername(), loginMethod, loginStatus);
        } catch (Exception e) {
            log.error("Failed to log login event: {}", e.getMessage());
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
    
    // Check Username Existence Method
    public boolean checkUsernameExists(CheckUsernameRequest request) {
        return userRepository.existsByUsernameAndBrandId(request.username(), request.brandId());
    }
    
    // Simple Check Username Existence Method (without brand ID)
    public boolean checkUsernameExists(SimpleCheckUsernameRequest request) {
        return userRepository.existsByUsername(request.username());
    }
    
    // Check Username Existence by username string
    public boolean checkUsernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    // Enhanced Forgot Password with Verification Code
    @Transactional
    public String sendPasswordResetCode(ForgotPasswordRequest request) {
        try {
            // Validate user ID format (should match MRTFY000001 pattern)
//            if (!request.userId().matches("MRTFY\\d{6}")) {
//                throw new RuntimeException("Invalid user ID format. Expected format: MRTFY000001");
//            }
            
            // Find user by userId and email to confirm they match
            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new RuntimeException("email not found"));
            
            // Validate that the email matches the user's email
            if (!user.getEmail().equals(request.email())) {
                throw new RuntimeException("User ID and email do not match");
            }
    
            // Clean up any existing codes for this user
            passwordResetCodeRepository.deleteByEmailAndUserId(request.email(), user.getId());
    
            // Generate 6-digit verification code
            String code = generateVerificationCode();
    
            // Save the code
            PasswordResetCode resetCode = PasswordResetCode.builder()
                    .email(request.email())
                    .userId(user.getId())
                    .code(code)
                    .build();
    
            passwordResetCodeRepository.save(resetCode);
    
            // Send email with verification code
            try {
                emailService.sendPasswordResetCode(request.email(), user.getUsername(), code);
                log.info("Password reset code sent to: {}", request.email());
            } catch (Exception e) {
                log.error("Failed to send password reset email: {}", e.getMessage());
                throw new RuntimeException("Failed to send verification code email");
            }
    
            return "Verification code sent successfully.";
        } catch (Exception e) {
            log.error("Error in sendPasswordResetCode: {}", e.getMessage());
            throw e;
        }
    }

    // Verify Reset Code Method - Only verify, don't mark as used yet
    public Map<String, Object> verifyResetCode(VerifyCodeRequest request) {
        try {
//            // Validate user ID format
//            if (!request.userId().matches("MRTFY\\d{6}")) {
//                throw new RuntimeException("Invalid user ID format. Expected format: MRTFY000001");
//            }
            
            Optional<PasswordResetCode> resetCodeOpt = passwordResetCodeRepository
                    .findByEmailAndCodeAndUsedFalse(request.email(), request.code());
    
            if (resetCodeOpt.isEmpty()) {
                throw new RuntimeException("Invalid verification code");
            }
    
            PasswordResetCode resetCode = resetCodeOpt.get();
            if (resetCode.isExpired()) {
                // Delete expired code
                passwordResetCodeRepository.delete(resetCode);
                throw new RuntimeException("Verification code has expired. Please request a new code.");
            }
    
            // Don't mark as used here - just verify it's valid
            return Map.of(
                    "message", "Code verified successfully. Proceed to set a new password.",
                    "verified", true,
                    "userId", resetCode.getUserId(),
                    "email", request.email(),
                    "nextStep", "You can now call /auth/set-new-password with the same code to reset your password"
                );
//            return "Code verified successfully. Proceed to set a new password.";
        } catch (Exception e) {
            log.error("Error in verifyResetCode: {}", e.getMessage());
            throw e;
        }
    }

    // Set New Password Method - Only works after code verification
    @Transactional
    public String setNewPassword(SetNewPasswordRequest request) {
        try {
            // Validate user ID format
            if (!request.userId().matches("MRTFY\\d{6}")) {
                throw new RuntimeException("Invalid user ID format. Expected format: MRTFY000001");
            }
            
            // Verify the code again before allowing password reset
            Optional<PasswordResetCode> resetCodeOpt = passwordResetCodeRepository
                    .findByEmailAndUserIdAndCodeAndUsedFalse(request.email(), request.userId(), request.code());
    
            if (resetCodeOpt.isEmpty()) {
                throw new RuntimeException("Invalid verification code or code already used");
            }
    
            PasswordResetCode resetCode = resetCodeOpt.get();
            if (resetCode.isExpired()) {
                // Delete expired code
                passwordResetCodeRepository.delete(resetCode);
                throw new RuntimeException("Verification code has expired. Please request a new one.");
            }
    
            // Find and update user password
            User user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
                    
            // Verify email matches
            if (!user.getEmail().equals(request.email())) {
                throw new RuntimeException("User ID and email do not match");
            }
    
            // Validate password complexity
            if (request.newPassword().length() < 8) {
                throw new RuntimeException("Password must be at least 8 characters long");
            }
    
            user.setPassword(passwordEncoder.encode(request.newPassword()));
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
    
            // Mark the code as used - now it can't be used again
            resetCode.setUsed(true);
            passwordResetCodeRepository.save(resetCode);
    
            return "Password has been reset successfully";
        } catch (Exception e) {
            log.error("Error in setNewPassword: {}", e.getMessage());
            throw e;
        }
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



package com.example.jwtauthenticator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.jwtauthenticator.dto.UserProfileUpdateRequestDTO;
import com.example.jwtauthenticator.dto.UserResponseDTO;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.util.TestDataFactory;


@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createTestUser();
        testUserId = testUser.getId(); // Use the String ID, not UUID
    }

    @Test
    void getUserById_success() {
        // Arrange
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        // Act
        Optional<UserResponseDTO> result = userService.getUserInfoByUserId(testUser.getId());

        // Assert
        assertTrue(result.isPresent());
        UserResponseDTO dto = result.get();
        assertEquals(testUser.getUserId(), dto.getUserId());
        assertEquals(testUser.getUsername(), dto.getUsername());
        assertEquals(testUser.getEmail(), dto.getEmail());
        assertEquals(testUser.getFirstName(), dto.getFirstName());
        assertEquals(testUser.getLastName(), dto.getLastName());
        assertEquals(testUser.getPhoneNumber(), dto.getPhoneNumber());
        assertEquals(testUser.getLocation(), dto.getLocation());
        assertEquals(testUser.getBrandId(), dto.getBrandId());
        assertEquals(testUser.isEmailVerified(), dto.isEmailVerified());
        assertEquals(testUser.isTfaEnabled(), dto.isTfaEnabled());
        
        verify(userRepository, times(1)).findById(testUser.getId());
    }

    @Test
    void getUserById_userNotFound() {
        // Arrange
        String nonExistentId = "NON_EXISTENT";
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act
        Optional<UserResponseDTO> result = userService.getUserInfoByUserId(nonExistentId);
        
        // Assert
        assertFalse(result.isPresent());
        verify(userRepository, times(1)).findById(nonExistentId);
    }

    @Test
    void getUserByUsername_success() {
        // Arrange
        String username = "testuser";
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

        // Act
        Optional<UserResponseDTO> result = userService.getUserInfoByUsername(username);

        // Assert
        assertTrue(result.isPresent());
        UserResponseDTO dto = result.get();
        assertEquals(testUser.getUsername(), dto.getUsername());
        assertEquals(testUser.getBrandId(), dto.getBrandId());
        
        verify(userRepository, times(1)).findByUsername(username);
    }

    @Test
    void getUserByUsername_userNotFound() {
        // Arrange
        String username = "nonexistent";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Act
        Optional<UserResponseDTO> result = userService.getUserInfoByUsername(username);
        
        // Assert
        assertFalse(result.isPresent());
        verify(userRepository, times(1)).findByUsername(username);
    }

    @Test
    void getUserByEmail_success() {
        // Arrange
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));

        // Act
        Optional<UserResponseDTO> result = userService.getUserInfoByEmail(email);

        // Assert
        assertTrue(result.isPresent());
        UserResponseDTO dto = result.get();
        assertEquals(testUser.getEmail(), dto.getEmail());
        assertEquals(testUser.getBrandId(), dto.getBrandId());
        
        verify(userRepository, times(1)).findByEmail(email);
    }

    @Test
    void getUserByEmail_userNotFound() {
        // Arrange
        String email = "nonexistent@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act
        Optional<UserResponseDTO> result = userService.getUserInfoByEmail(email);
        
        // Assert
        assertFalse(result.isPresent());
        verify(userRepository, times(1)).findByEmail(email);
    }

    @Test
    void updateUserProfile_success() {
        // Arrange
        UserProfileUpdateRequestDTO updateRequest = UserProfileUpdateRequestDTO.builder()
            .id(testUser.getId())
            .firstName("John Updated")
            .surname("Doe Updated")
            .build();
        
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        Optional<UserResponseDTO> result = userService.updateProfile(updateRequest);

        // Assert
        assertTrue(result.isPresent());
        UserResponseDTO dto = result.get();
        assertEquals("John Updated", dto.getFirstName());
        assertEquals("Doe Updated", dto.getLastName());
        
        verify(userRepository, times(1)).findById(testUser.getId());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void updateUserProfile_userNotFound() {
        // Arrange
        UserProfileUpdateRequestDTO updateRequest = UserProfileUpdateRequestDTO.builder()
            .id("NON_EXISTENT")
            .firstName("John Updated")
            .surname("Doe Updated")
            .build();
        
        when(userRepository.findById("NON_EXISTENT")).thenReturn(Optional.empty());

        // Act
        Optional<UserResponseDTO> result = userService.updateProfile(updateRequest);
        
        // Assert
        assertFalse(result.isPresent());
        verify(userRepository, times(1)).findById("NON_EXISTENT");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserProfile_withUserProfileUpdateRequestDTO_success() {
        // Arrange
        UserProfileUpdateRequestDTO updateRequest = UserProfileUpdateRequestDTO.builder()
            .id(testUserId)
            .firstName("John Updated")
            .surname("Doe Updated")
            .phoneNumber("+9876543210")
            .city("Los Angeles")
            .build();
        
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        Optional<UserResponseDTO> result = userService.updateProfile(updateRequest);

        // Assert
        assertTrue(result.isPresent());
        UserResponseDTO dto = result.get();
        assertEquals("John Updated", dto.getFirstName());
        assertEquals("Doe Updated", dto.getLastName());
        assertEquals("+9876543210", dto.getPhoneNumber());
        
        verify(userRepository, times(1)).findById(testUserId);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void updateUserProfile_withNullFields_shouldHandleGracefully() {
        // Arrange
        UserProfileUpdateRequestDTO updateRequest = UserProfileUpdateRequestDTO.builder()
            .id(testUserId)
            .firstName(null)
            .surname(null)
            .phoneNumber(null)
            .city(null)
            .build();
        
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        Optional<UserResponseDTO> result = userService.updateProfile(updateRequest);

        // Assert
        assertTrue(result.isPresent());
        verify(userRepository, times(1)).findById(testUserId);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void updateUserProfile_withEmptyFields_shouldHandleGracefully() {
        // Arrange
        UserProfileUpdateRequestDTO updateRequest = UserProfileUpdateRequestDTO.builder()
            .id(testUserId)
            .firstName("")
            .surname("")
            .phoneNumber("")
            .city("")
            .build();
        
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        Optional<UserResponseDTO> result = userService.updateProfile(updateRequest);

        // Assert
        assertTrue(result.isPresent());
        verify(userRepository, times(1)).findById(testUserId);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void getUserById_withNullId_shouldReturnEmpty() {
        // Act
        Optional<UserResponseDTO> result = userService.getUserInfoByUserId(null);
        
        // Assert
        assertFalse(result.isPresent());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void getUserByUsername_withNullUsername_shouldReturnEmpty() {
        // Act
        Optional<UserResponseDTO> result = userService.getUserInfoByUsername(null);
        
        // Assert
        assertFalse(result.isPresent());
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void getUserByEmail_withNullEmail_shouldReturnEmpty() {
        // Act
        Optional<UserResponseDTO> result = userService.getUserInfoByEmail(null);
        
        // Assert
        assertFalse(result.isPresent());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void updateUserProfile_withNullRequest_shouldReturnEmpty() {
        // Act
        Optional<UserResponseDTO> result = userService.updateProfile(null);
        
        // Assert
        assertFalse(result.isPresent());
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }
}
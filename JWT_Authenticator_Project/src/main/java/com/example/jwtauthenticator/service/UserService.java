package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.UserResponseDTO;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

	@Autowired
    private UserRepository userRepository;

    public Optional<UserResponseDTO> getUserInfoByUserId(String userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        return userOptional.map(UserResponseDTO::fromEntity);
    }

    // You might also want to add methods to fetch by username, email, etc.
    public Optional<UserResponseDTO> getUserInfoByUsername(String username) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        return userOptional.map(UserResponseDTO::fromEntity);
    }

    public Optional<UserResponseDTO> getUserInfoByEmail(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        return userOptional.map(UserResponseDTO::fromEntity);
    }
}

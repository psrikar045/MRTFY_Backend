package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.BrandDataResponse;
import com.example.jwtauthenticator.dto.UserIdDTO;
import com.example.jwtauthenticator.dto.UserResponseDTO;
import com.example.jwtauthenticator.service.CategoryService;
import com.example.jwtauthenticator.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Data", description = "Endpoints for retrieving stored User information")
public class UserController {
 
	@Autowired
    private  UserService userService;


    @PostMapping("/get-by-id") 
    @Operation(
            summary = "Get profile by ID",
            description = "Retrieve complete user information",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
        )
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found", 
                        content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
        })
    public ResponseEntity<UserResponseDTO> getUserByIdPost(@Valid @RequestBody UserIdDTO requestDTO) {
        return userService.getUserInfoByUserId(requestDTO.getId())
                .map(userResponseDTO -> new ResponseEntity<>(userResponseDTO, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
	
    @GetMapping("/userId/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable String id) {
        return userService.getUserInfoByUserId(id)
                .map(userResponseDTO -> new ResponseEntity<>(userResponseDTO, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // Example of fetching by username (you might need to secure this endpoint appropriately)
    @GetMapping("/username/{username}")
    public ResponseEntity<UserResponseDTO> getUserByUsername(@PathVariable String username) {
        return userService.getUserInfoByUsername(username)
                .map(userResponseDTO -> new ResponseEntity<>(userResponseDTO, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // Example of fetching by email (you might need to secure this endpoint appropriately)
    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponseDTO> getUserByEmail(@PathVariable String email) {
        return userService.getUserInfoByEmail(email)
                .map(userResponseDTO -> new ResponseEntity<>(userResponseDTO, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}

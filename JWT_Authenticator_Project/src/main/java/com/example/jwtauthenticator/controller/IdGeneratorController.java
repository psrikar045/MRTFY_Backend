package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.service.IdGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/id-generator")
@Tag(name = "ID Generator", description = "Flexible auto-incrementing ID generator APIs")
public class IdGeneratorController {

    @Autowired
    private IdGeneratorService idGeneratorService;

    @PostMapping("/generate")
    @Operation(summary = "Generate next ID with default prefix", 
               description = "Generate next ID using default prefix from application.properties")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "ID generated successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to generate ID")
    })
    public ResponseEntity<?> generateNextId() {
        try {
            String generatedId = idGeneratorService.generateNextId();
            return ResponseEntity.ok(Map.of(
                "id", generatedId,
                "success", true,
                "message", "ID generated successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to generate ID: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/generate/{prefix}")
    @Operation(summary = "Generate next ID with custom prefix", 
               description = "Generate next ID using custom prefix (e.g., MKTY, MRTFY)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "ID generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid prefix"),
        @ApiResponse(responseCode = "500", description = "Failed to generate ID")
    })
    public ResponseEntity<?> generateNextIdWithPrefix(
            @Parameter(description = "Custom prefix for ID generation", example = "MKTY")
            @PathVariable String prefix) {
        try {
            String generatedId = idGeneratorService.generateNextId(prefix);
            return ResponseEntity.ok(Map.of(
                "id", generatedId,
                "prefix", prefix.toUpperCase(),
                "success", true,
                "message", "ID generated successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to generate ID: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/preview")
    @Operation(summary = "Preview next ID with default prefix", 
               description = "Preview what the next ID would be without generating it")
    public ResponseEntity<?> previewNextId() {
        try {
            String previewId = idGeneratorService.previewNextId(null);
            Long currentNumber = idGeneratorService.getCurrentNumber(null);
            return ResponseEntity.ok(Map.of(
                "nextId", previewId,
                "currentNumber", currentNumber,
                "success", true
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to preview ID: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/preview/{prefix}")
    @Operation(summary = "Preview next ID with custom prefix", 
               description = "Preview what the next ID would be for a custom prefix without generating it")
    public ResponseEntity<?> previewNextIdWithPrefix(
            @Parameter(description = "Prefix to preview", example = "MKTY")
            @PathVariable String prefix) {
        try {
            String previewId = idGeneratorService.previewNextId(prefix);
            Long currentNumber = idGeneratorService.getCurrentNumber(prefix);
            return ResponseEntity.ok(Map.of(
                "nextId", previewId,
                "prefix", prefix.toUpperCase(),
                "currentNumber", currentNumber,
                "success", true
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to preview ID: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/current/{prefix}")
    @Operation(summary = "Get current number for prefix", 
               description = "Get the current number for a specific prefix")
    public ResponseEntity<?> getCurrentNumber(
            @Parameter(description = "Prefix to check", example = "MRTFY")
            @PathVariable String prefix) {
        try {
            Long currentNumber = idGeneratorService.getCurrentNumber(prefix);
            boolean exists = idGeneratorService.prefixExists(prefix);
            return ResponseEntity.ok(Map.of(
                "prefix", prefix.toUpperCase(),
                "currentNumber", currentNumber,
                "exists", exists,
                "success", true
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to get current number: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/prefixes")
    @Operation(summary = "Get all available prefixes", 
               description = "Get list of all prefixes that have been used")
    public ResponseEntity<?> getAllPrefixes() {
        try {
            List<String> prefixes = idGeneratorService.getAllPrefixes();
            return ResponseEntity.ok(Map.of(
                "prefixes", prefixes,
                "count", prefixes.size(),
                "success", true
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to get prefixes: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/reset/{prefix}")
    @Operation(summary = "Reset sequence for prefix", 
               description = "Reset the sequence for a prefix to start from a specific number (use with caution!)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Sequence reset successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "500", description = "Failed to reset sequence")
    })
    public ResponseEntity<?> resetSequence(
            @Parameter(description = "Prefix to reset", example = "MRTFY")
            @PathVariable String prefix,
            @Parameter(description = "Number to start from", example = "0")
            @RequestParam(defaultValue = "0") Long startNumber) {
        try {
            idGeneratorService.resetSequence(prefix, startNumber);
            return ResponseEntity.ok(Map.of(
                "prefix", prefix.toUpperCase(),
                "startNumber", startNumber,
                "success", true,
                "message", "Sequence reset successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to reset sequence: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/user-id/generate")
    @Operation(summary = "Generate DOMBR user ID", 
               description = "Generate a unique user ID with DOMBR prefix and 6-digit sequential number")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User ID generated successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to generate user ID")
    })
    public ResponseEntity<?> generateDombrUserId() {
        try {
            String userId = idGeneratorService.generateDombrUserId();
            return ResponseEntity.ok(Map.of(
                "userId", userId,
                "success", true,
                "message", "User ID generated successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to generate user ID: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/user-id/preview")
    @Operation(summary = "Preview next DOMBR user ID", 
               description = "Preview what the next DOMBR user ID would be without generating it")
    public ResponseEntity<?> previewNextDombrUserId() {
        try {
            String previewId = idGeneratorService.previewNextDombrUserId();
            Long currentNumber = idGeneratorService.getCurrentNumber("DOMBR");
            return ResponseEntity.ok(Map.of(
                "nextUserId", previewId,
                "currentNumber", currentNumber,
                "success", true
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to preview user ID: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/user-id/simple")
    @Operation(summary = "Generate a simple DOMBR user ID", 
               description = "Generate a DOMBR user ID using a simple method that doesn't rely on database sequences")
    public ResponseEntity<?> generateSimpleDombrUserId() {
        try {
            String userId = idGeneratorService.generateSimpleDombrUserId();
            return ResponseEntity.ok(Map.of(
                "userId", userId,
                "method", "simple",
                "success", true
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to generate simple user ID: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/user-id/init-sequence")
    @Operation(summary = "Initialize DOMBR sequence", 
               description = "Initialize the DOMBR sequence and function for sequential ID generation")
    public ResponseEntity<?> initDombrSequence() {
        try {
            // Execute the SQL to create the sequence and function
            idGeneratorService.initializeDombrSequence();
            
            // Test the sequence by generating a few IDs
            String id1 = idGeneratorService.generateDombrUserId();
            String id2 = idGeneratorService.generateDombrUserId();
            String id3 = idGeneratorService.generateDombrUserId();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "DOMBR sequence initialized successfully",
                "testIds", List.of(id1, id2, id3)
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to initialize DOMBR sequence: " + e.getMessage(),
                "error", e.toString()
            ));
        }
    }
}
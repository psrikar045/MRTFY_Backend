package com.example.jwtauthenticator.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.jwtauthenticator.dto.MasterDataHierarchyDTO;
import com.example.jwtauthenticator.service.CategoryService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Category Data", description = "Endpoints for retrieving stored Category information")
public class CategoryController {

	private final CategoryService categoryService;
	
	 @GetMapping("/hierarchy")
	    @Operation(
	        summary = "Get all hierarchical master data",
	        description = "Retrieve all brand categories with their subcategories and regions with their countries in a complete hierarchical structure.",
	        security = { @SecurityRequirement(name = "Bearer Authentication") }
	    )
	    @ApiResponses(value = {
	        @ApiResponse(responseCode = "200", description = "Hierarchical master data successfully retrieved",
	                    content = @Content(schema = @Schema(implementation = MasterDataHierarchyDTO.class))),
	        @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required or token is invalid"),
	        @ApiResponse(responseCode = "500", description = "Internal Server Error - Failed to retrieve data due to a server-side issue")
	    })
	    public ResponseEntity<?> getHierarchyData() {
	        try {
	            MasterDataHierarchyDTO data = categoryService.getCategorizedHierarchyData();
	            // In case no data is found (e.g., empty lists), still return 200 OK with empty lists
	            return ResponseEntity.ok(data);
	        } catch (Exception e) {
	            // Log the exception (e.g., using SLF4J, Log4j)
	            e.printStackTrace();
	            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                                 .body("An error occurred while fetching hierarchical master data: " + e.getMessage());
	        }
	    }
}

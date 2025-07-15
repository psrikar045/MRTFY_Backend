package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {
    private Long id;
    private String name;
    private String description;
    private String iconURL;
    private List<SubCategoryDTO> subCategories;
    // You can add more fields if needed, e.g., externalId, displayOrder
}

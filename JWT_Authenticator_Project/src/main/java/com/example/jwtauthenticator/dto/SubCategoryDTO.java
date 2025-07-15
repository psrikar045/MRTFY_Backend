package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubCategoryDTO {
    private Long id;
    private String name;
    private String description;
    private String iconURL;
    // You can add more fields if needed, e.g., externalId, displayOrder
}

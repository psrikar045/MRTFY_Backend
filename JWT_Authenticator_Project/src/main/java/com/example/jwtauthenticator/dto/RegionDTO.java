package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegionDTO {
    private Long id;
    private String name;
    private String code;
    private String iconURL;
    private List<CountryDTO> countries;
    // You can add more fields if needed, e.g., externalId, displayOrder
}

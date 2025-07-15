package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MasterDataHierarchyDTO {
    private List<CategoryDTO> categories;
    private List<RegionDTO> regions;
}

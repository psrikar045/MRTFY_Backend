package com.example.jwtauthenticator.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.jwtauthenticator.dto.CategoryDTO;
import com.example.jwtauthenticator.dto.CountryDTO;
import com.example.jwtauthenticator.dto.MasterDataHierarchyDTO;
import com.example.jwtauthenticator.dto.RegionDTO;
import com.example.jwtauthenticator.dto.SubCategoryDTO;
import com.example.jwtauthenticator.entity.BrandCategory;
import com.example.jwtauthenticator.entity.BrandSubCategory;
import com.example.jwtauthenticator.entity.Country;
import com.example.jwtauthenticator.entity.Region;
import com.example.jwtauthenticator.repository.BrandCategoryRepository;
import com.example.jwtauthenticator.repository.BrandSubCategoryRepository;
import com.example.jwtauthenticator.repository.CountryRepository;
import com.example.jwtauthenticator.repository.RegionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

	 private final BrandCategoryRepository catRepo;
	 private final BrandSubCategoryRepository subCatRepo;
	 private final RegionRepository regionRepo;
	 private final CountryRepository countryRepo;
	 
	 public MasterDataHierarchyDTO getCategorizedHierarchyData() {
	        // Fetch all data
	        List<BrandCategory> allCategories = catRepo.findAll();
	        List<BrandSubCategory> allSubCategories = subCatRepo.findAll();
	        List<Region> allRegions = regionRepo.findAll();
	        List<Country> allCountries = countryRepo.findAll();

	        // Map Categories and their Subcategories
	        List<CategoryDTO> categoryDTOs = allCategories.stream()
	            .map(category -> {
	                CategoryDTO categoryDTO = new CategoryDTO(
	                    category.getId(),
	                    category.getCategoryName(),
	                    category.getCategoryDescription(),
	                    category.getIconURL(),
	                    null // Subcategories will be set next
	                );
	                List<SubCategoryDTO> subCategoryDTOs = allSubCategories.stream()
	                    .filter(sub -> sub.getCategoryId().equals(category.getId()))
	                    .map(sub -> new SubCategoryDTO(
	                        sub.getId(),
	                        sub.getSubCategoryName(),
	                        sub.getSubCategoryDescription(),
	                        sub.getIconURL()
	                    ))
	                    .collect(Collectors.toList());
	                categoryDTO.setSubCategories(subCategoryDTOs);
	                return categoryDTO;
	            })
	            .collect(Collectors.toList());

	        // Map Regions and their Countries
	        List<RegionDTO> regionDTOs = allRegions.stream()
	            .map(region -> {
	                RegionDTO regionDTO = new RegionDTO(
	                    region.getId(),
	                    region.getRegionName(),
	                    region.getRegionCode(),
	                    region.getIconURL(),
	                    null // Countries will be set next
	                );
	                List<CountryDTO> countryDTOs = allCountries.stream()
	                    .filter(country -> country.getRegionId().equals(region.getId()))
	                    .map(country -> new CountryDTO(
	                        country.getId(),
	                        country.getCountryName(),
	                        country.getCountryCode(),
	                        country.getFlagURL()
	                    ))
	                    .collect(Collectors.toList());
	                regionDTO.setCountries(countryDTOs);
	                return regionDTO;
	            })
	            .collect(Collectors.toList());

	        return new MasterDataHierarchyDTO(categoryDTOs, regionDTOs);
	    }
	 
}

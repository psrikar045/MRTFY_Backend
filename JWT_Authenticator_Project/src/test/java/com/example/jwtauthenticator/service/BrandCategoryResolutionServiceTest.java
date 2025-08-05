package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.Brand;
import com.example.jwtauthenticator.entity.BrandCategory;
import com.example.jwtauthenticator.entity.BrandSubCategory;
import com.example.jwtauthenticator.repository.BrandCategoryRepository;
import com.example.jwtauthenticator.repository.BrandSubCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BrandCategoryResolutionServiceTest {

    @Mock
    private BrandCategoryRepository brandCategoryRepository;

    @Mock
    private BrandSubCategoryRepository brandSubCategoryRepository;

    @InjectMocks
    private BrandCategoryResolutionService brandCategoryResolutionService;

    private BrandCategory testCategory;
    private BrandSubCategory testSubCategory;

    @BeforeEach
    void setUp() {
        testCategory = BrandCategory.builder()
                .id(1L)
                .categoryName("Technology")
                .categoryDescription("Technology companies")
                .isActive(true)
                .createdDate(LocalDateTime.now())
                .lastModifiedDate(LocalDateTime.now())
                .build();

        testSubCategory = BrandSubCategory.builder()
                .id(10L)
                .categoryId(1L)
                .subCategoryName("Software")
                .subCategoryDescription("Software development companies")
                .isActive(true)
                .createdDate(LocalDateTime.now())
                .lastModifiedDate(LocalDateTime.now())
                .build();
    }

    @Test
    void testResolveCategoryIds_MatchesCategory() {
        // Given
        lenient().when(brandCategoryRepository.findByCategoryNameIgnoreCaseAndIsActive("Technology", true))
                .thenReturn(Optional.of(testCategory));

        // When
        BrandCategoryResolutionService.CategoryResolutionResult result = 
                brandCategoryResolutionService.resolveCategoryIds("Technology");

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getCategoryId());
        assertNull(result.getSubCategoryId());
    }

    @Test
    void testResolveCategoryIds_MatchesSubCategory() {
        // Given
        when(brandCategoryRepository.findByCategoryNameIgnoreCaseAndIsActive("Software", true))
                .thenReturn(Optional.empty());
        when(brandSubCategoryRepository.findBySubCategoryNameIgnoreCaseAndIsActive("Software", true))
                .thenReturn(Optional.of(testSubCategory));

        // When
        BrandCategoryResolutionService.CategoryResolutionResult result = 
                brandCategoryResolutionService.resolveCategoryIds("Software");

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getCategoryId()); // Parent category ID
        assertEquals(10L, result.getSubCategoryId());
    }

    @Test
    void testResolveCategoryIds_NoMatch() {
        // Given
        lenient().when(brandCategoryRepository.findByCategoryNameIgnoreCaseAndIsActive(anyString(), eq(true)))
                .thenReturn(Optional.empty());
        lenient().when(brandSubCategoryRepository.findBySubCategoryNameIgnoreCaseAndIsActive(anyString(), eq(true)))
                .thenReturn(Optional.empty());

        // When
        BrandCategoryResolutionService.CategoryResolutionResult result = 
                brandCategoryResolutionService.resolveCategoryIds("Unknown Industry");

        // Then
        assertNotNull(result);
        assertNull(result.getCategoryId());
        assertNull(result.getSubCategoryId());
    }

    @Test
    void testResolveCategoryIds_NullIndustry() {
        // When
        BrandCategoryResolutionService.CategoryResolutionResult result = 
                brandCategoryResolutionService.resolveCategoryIds(null);

        // Then
        assertNotNull(result);
        assertNull(result.getCategoryId());
        assertNull(result.getSubCategoryId());
    }

    @Test
    void testResolveCategoryIds_EmptyIndustry() {
        // When
        BrandCategoryResolutionService.CategoryResolutionResult result = 
                brandCategoryResolutionService.resolveCategoryIds("");

        // Then
        assertNotNull(result);
        assertNull(result.getCategoryId());
        assertNull(result.getSubCategoryId());
    }

    @Test
    void testResolveCategoryIds_CaseInsensitive() {
        // Given
        when(brandCategoryRepository.findByCategoryNameIgnoreCaseAndIsActive("TECHNOLOGY", true))
                .thenReturn(Optional.of(testCategory));

        // When
        BrandCategoryResolutionService.CategoryResolutionResult result = 
                brandCategoryResolutionService.resolveCategoryIds("TECHNOLOGY");

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getCategoryId());
        assertNull(result.getSubCategoryId());
    }

    @Test
    void testSetCategoryIds() {
        // Given
        Brand brand = Brand.builder()
                .name("Test Brand")
                .industry("Technology")
                .build();

        when(brandCategoryRepository.findByCategoryNameIgnoreCaseAndIsActive("Technology", true))
                .thenReturn(Optional.of(testCategory));

        // When
        brandCategoryResolutionService.setCategoryIds(brand);

        // Then
        assertEquals(1L, brand.getCategoryId());
        assertNull(brand.getSubCategoryId());
    }

    @Test
    void testSetCategoryIds_NullBrand() {
        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> brandCategoryResolutionService.setCategoryIds(null));
    }
}
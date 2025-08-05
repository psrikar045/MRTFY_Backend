package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "brands", indexes = {
    @Index(name = "idx_brand_name", columnList = "name"),
    @Index(name = "idx_brand_website", columnList = "website"),
    @Index(name = "idx_brand_created", columnList = "createdAt")
})
@NamedEntityGraphs({
    @NamedEntityGraph(
        name = "Brand.withAssets",
        attributeNodes = @NamedAttributeNode("assets")
    ),
    @NamedEntityGraph(
        name = "Brand.withColors", 
        attributeNodes = @NamedAttributeNode("colors")
    ),
    @NamedEntityGraph(
        name = "Brand.withFonts",
        attributeNodes = @NamedAttributeNode("fonts")
    ),
    @NamedEntityGraph(
        name = "Brand.withSocialLinks",
        attributeNodes = @NamedAttributeNode("socialLinks")
    ),
    @NamedEntityGraph(
        name = "Brand.withImages",
        attributeNodes = @NamedAttributeNode("images")
    )
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Brand {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, unique = true)
    private String website;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private String industry;
    private String location;
    private String founded;
    private String companyType;
    private String employees;
    @Column(name = "category_id")
    private Long categoryId;
    
    @Column(name = "sub_category_id")
    private Long subCategoryId;
    
    @Column(name = "specialties", columnDefinition = "TEXT")
    private String specialties; // JSON array of specialties
    
    @Column(name = "company_locations", columnDefinition = "TEXT")
    private String locations; // JSON array of locations
    
    // Extraction metadata
    @Column(name = "extraction_time_seconds")
    private Double extractionTimeSeconds;
    
    @Column(name = "last_extraction_timestamp")
    private LocalDateTime lastExtractionTimestamp;
    
    @Column(name = "extraction_message", columnDefinition = "TEXT")
    private String extractionMessage;
    
    // Freshness scoring for future AI implementation
    @Column(name = "freshness_score")
    @Builder.Default
    private Integer freshnessScore = 100;
    
    @Column(name = "needs_update")
    @Builder.Default
    private Boolean needsUpdate = false;
    
    @Column(name = "is_brand_claimed")
    @Builder.Default
    private Boolean isBrandClaimed = false;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Relationships with batch fetching to optimize N+1 queries
    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 25)
    @Builder.Default
    private List<BrandAsset> assets = new ArrayList<>();
    
    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 25)
    @Builder.Default
    private List<BrandColor> colors = new ArrayList<>();
    
    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 25)
    @Builder.Default
    private List<BrandFont> fonts = new ArrayList<>();
    
    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 25)
    @Builder.Default
    private List<BrandSocialLink> socialLinks = new ArrayList<>();
    
    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 25)
    @Builder.Default
    private List<BrandImage> images = new ArrayList<>();
    
    // Helper methods for managing relationships
    public void addAsset(BrandAsset asset) {
        assets.add(asset);
        asset.setBrand(this);
    }
    
    public void addColor(BrandColor color) {
        colors.add(color);
        color.setBrand(this);
    }
    
    public void addFont(BrandFont font) {
        fonts.add(font);
        font.setBrand(this);
    }
    
    public void addSocialLink(BrandSocialLink socialLink) {
        socialLinks.add(socialLink);
        socialLink.setBrand(this);
    }
    
    public void addImage(BrandImage image) {
        images.add(image);
        image.setBrand(this);
    }
}
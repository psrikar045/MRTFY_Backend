package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "brandsubcategories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandSubCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SubCategoryID")
    private Long id;

    @Column(name = "CategoryID", nullable = false)
    private Long categoryId; // Foreign key reference

    // If you prefer a direct JPA relationship, uncomment the following and comment out categoryId:
    // @ManyToOne
    // @JoinColumn(name = "CategoryID", nullable = false, insertable = false, updatable = false)
    // private BrandCategory brandCategory;

    @Column(name = "SubCategoryName", nullable = false)
    private String subCategoryName;

    @Column(name = "SubCategoryDescription")
    private String subCategoryDescription;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;

    @Column(name = "CreatedDate", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "LastModifiedDate", nullable = false)
    private LocalDateTime lastModifiedDate;

    @Column(name = "ExternalID", unique = true)
    private String externalId;

    @Column(name = "DisplayOrder")
    private Integer displayOrder;

    @Column(name = "IconURL")
    private String iconURL;

    @Column(name = "MetaData", columnDefinition = "jsonb")
    private String metaData; // Stores JSON as a String

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        lastModifiedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedDate = LocalDateTime.now();
    }
}

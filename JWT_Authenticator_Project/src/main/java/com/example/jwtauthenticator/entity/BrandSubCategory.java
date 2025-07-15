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
    @Column(name = "subcategoryid")
    private Long id;

    @Column(name = "categoryid", nullable = false)
    private Long categoryId; // Foreign key reference

    // If you prefer a direct JPA relationship, uncomment the following and comment out categoryId:
    // @ManyToOne
    // @JoinColumn(name = "CategoryID", nullable = false, insertable = false, updatable = false)
    // private BrandCategory brandCategory;

    @Column(name = "subcategoryname", nullable = false)
    private String subCategoryName;

    @Column(name = "sub_category_description")
    private String subCategoryDescription;

    @Column(name = "isactive", nullable = false)
    private Boolean isActive = true;

    @Column(name = "createddate", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "lastmodifieddate", nullable = false)
    private LocalDateTime lastModifiedDate;

    @Column(name = "externalid", unique = true)
    private String externalId;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "iconurl")
    private String iconURL;

    @Column(name = "metadata", columnDefinition = "jsonb")
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

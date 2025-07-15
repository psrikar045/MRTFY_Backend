package com.example.jwtauthenticator.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "brandcategories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CategoryID")
    private Long id;

    @Column(name = "CategoryName", nullable = false, unique = true)
    private String categoryName;

    @Column(name = "CategoryDescription")
    private String categoryDescription;

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

    // Lifecycle callbacks for CreatedDate and LastModifiedDate (if not using Hibernate specific annotations like @CreationTimestamp, @UpdateTimestamp)
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

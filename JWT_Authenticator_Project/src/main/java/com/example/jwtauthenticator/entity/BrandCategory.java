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
    @Column(name = "categoryid")
    private Long id;

    @Column(name = "categoryname", nullable = false, unique = true)
    private String categoryName;

    @Column(name = "category_description")
    private String categoryDescription;

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

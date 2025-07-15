package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "regions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RegionID")
    private Long id;

    @Column(name = "RegionName", nullable = false, unique = true)
    private String regionName;

    @Column(name = "RegionCode", unique = true)
    private String regionCode;

    @Column(name = "RegionDescription")
    private String regionDescription;

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

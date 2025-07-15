package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "countries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Country {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CountryID")
    private Long id;

    @Column(name = "RegionID", nullable = false)
    private Long regionId; // Foreign key reference

    // If you prefer a direct JPA relationship, uncomment the following and comment out regionId:
    // @ManyToOne
    // @JoinColumn(name = "RegionID", nullable = false, insertable = false, updatable = false)
    // private Region region;

    @Column(name = "CountryName", nullable = false)
    private String countryName;

    @Column(name = "CountryCode", unique = true)
    private String countryCode;

    @Column(name = "CountryDescription")
    private String countryDescription;

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

    @Column(name = "FlagURL")
    private String flagURL;

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
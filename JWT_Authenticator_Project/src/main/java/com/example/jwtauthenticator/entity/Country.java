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
    @Column(name = "countryid")
    private Long id;

    @Column(name = "regionid", nullable = false)
    private Long regionId; // Foreign key reference

    // If you prefer a direct JPA relationship, uncomment the following and comment out regionId:
    // @ManyToOne
    // @JoinColumn(name = "RegionID", nullable = false, insertable = false, updatable = false)
    // private Region region;

    @Column(name = "countryname", nullable = false)
    private String countryName;

    @Column(name = "country_code", unique = true)
    private String countryCode;

    @Column(name = "country_description")
    private String countryDescription;

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

    @Column(name = "flagurl")
    private String flagURL;

    @Column(name = "meta_data", columnDefinition = "jsonb")
    private String metaData; // Stores JSON as a String

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metaData2; // Stores JSON as a String
    
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
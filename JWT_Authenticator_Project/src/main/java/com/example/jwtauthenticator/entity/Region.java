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
    @Column(name = "regionid")
    private Long id;

    @Column(name = "regionname", nullable = false, unique = true)
    private String regionName;

    @Column(name = "region_code", unique = true)
    private String regionCode;

    @Column(name = "region_description")
    private String regionDescription;

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

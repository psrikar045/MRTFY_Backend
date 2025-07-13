package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "brand_social_links", indexes = {
    @Index(name = "idx_social_brand", columnList = "brand_id"),
    @Index(name = "idx_social_platform", columnList = "platform")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandSocialLink {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;
    
    @Column(name = "extraction_error", columnDefinition = "TEXT")
    private String extractionError; // For cases like "LinkedIn extraction timeout"
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public enum Platform {
        TWITTER,
        LINKEDIN,
        FACEBOOK,
        YOUTUBE,
        INSTAGRAM,
        TIKTOK,
        OTHER
    }
}
package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "brand_colors", indexes = {
    @Index(name = "idx_color_brand", columnList = "brand_id"),
    @Index(name = "idx_color_hex", columnList = "hexCode")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandColor {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;
    
    @Column(name = "hex_code", nullable = false, length = 7)
    private String hexCode;
    
    @Column(name = "rgb_value", length = 20)
    private String rgbValue;
    
    @Column(name = "brightness")
    private Integer brightness;
    
    @Column(name = "color_name", length = 100)
    private String colorName;
    
    @Column(name = "usage_context", length = 100)
    private String usageContext; // e.g., "button_bg", "h1_text", etc.
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
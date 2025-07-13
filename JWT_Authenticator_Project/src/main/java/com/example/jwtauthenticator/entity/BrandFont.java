package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "brand_fonts", indexes = {
    @Index(name = "idx_font_brand", columnList = "brand_id"),
    @Index(name = "idx_font_name", columnList = "fontName")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandFont {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;
    
    @Column(name = "font_name", nullable = false, length = 100)
    private String fontName;
    
    @Column(name = "font_type", length = 50)
    private String fontType; // e.g., "heading", "body"
    
    @Column(name = "font_stack", columnDefinition = "TEXT")
    private String fontStack; // e.g., "Gilroy, sans-serif"
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
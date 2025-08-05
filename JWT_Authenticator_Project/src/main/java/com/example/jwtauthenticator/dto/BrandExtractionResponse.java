package com.example.jwtauthenticator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO representing the response from the external brand extraction API
 */
@Data
public class BrandExtractionResponse {
    
    @JsonProperty("Logo")
    private LogoData logo;
    
    @JsonProperty("Colors")
    private List<ColorData> colors;
    
    @JsonProperty("Fonts")
    private List<FontData> fonts;
    
    @JsonProperty("Images")
    private List<ImageData> images;
    
    @JsonProperty("Company")
    private CompanyData company;
    
    @JsonProperty("_performance")
    private PerformanceData performance;
    
    @JsonProperty("_message")
    private String message;
    
    @Data
    public static class LogoData {
        @JsonProperty("Logo")
        private String logo;
        
        @JsonProperty("Symbol")
        private String symbol;
        
        @JsonProperty("Icon")
        private String icon;
        
        @JsonProperty("Banner")
        private String banner;
        
        @JsonProperty("LinkedInBanner")
        private String linkedInBanner;
        
        @JsonProperty("LinkedInLogo")
        private String linkedInLogo;
    }
    
    @Data
    public static class ColorData {
        private String hex;
        private String rgb;
        private Integer brightness;
        private String name;
        
        // Fields for image-based color objects (logo/banner colors)
        private Integer width;
        private Integer height;
        private List<String> colors;
    }
    
    @Data
    public static class FontData {
        private String name;
        private String type;
        private String stack;
    }
    
    @Data
    public static class ImageData {
        private String src;
        private String alt;
    }
    
    @Data
    public static class CompanyData {
        @JsonProperty("Name")
        private String name;
        
        @JsonProperty("Description")
        private String description;
        
        @JsonProperty("Industry")
        private String industry;
        
        @JsonProperty("Location")
        private String location;
        
        @JsonProperty("Founded")
        private String founded;
        
        @JsonProperty("CompanyType")
        private String companyType;
        
        @JsonProperty("Employees")
        private String employees;
        
        @JsonProperty("Website")
        private String website;
        
        @JsonProperty("SocialLinks")
        private Map<String, String> socialLinks;
        
        @JsonProperty("LinkedInError")
        private String linkedInError;
        
        @JsonProperty("CompanySize")
        private String companySize;
        
        @JsonProperty("Headquarters")
        private String headquarters;
        
        @JsonProperty("Type")
        private String type;
        
        @JsonProperty("Specialties")
        private List<String> specialties;
        
        @JsonProperty("Locations")
        private List<String> locations;
    }
    
    @Data
    public static class PerformanceData {
        private Double extractionTimeSeconds;
        private LocalDateTime timestamp;
    }
}
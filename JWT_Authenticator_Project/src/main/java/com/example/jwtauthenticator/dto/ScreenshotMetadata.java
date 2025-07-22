package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenshotMetadata {
    private String type;
    private String filename;
    private long size;
    private String lastModified;
    private String accessUrl;
}
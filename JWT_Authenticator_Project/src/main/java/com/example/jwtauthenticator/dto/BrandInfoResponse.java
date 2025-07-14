package com.example.jwtauthenticator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrandInfoResponse {
    private String status;
    private String message;
    private String resolvedUrl;

    public static BrandInfoResponse success(String resolvedUrl) {
        return new BrandInfoResponse("success", null, resolvedUrl);
    }

    public static BrandInfoResponse error(String message) {
        return new BrandInfoResponse("error", message, null);
    }
}
package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Size;
import java.util.Date; // Still needed for dob (futureT1)

import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequestDTO {

    private String firstName;
    private String surname; 
    
    @Size(max = 20) 
    private String nationalCode; // Maps to futureI1
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Date dob;

    private String educationLevel; // Maps to futureV1
    private String phoneCountry;   // Maps to futureV2
    private String country;        // Maps to futureV3
    private String city;           // Maps to futureV4

    private String phoneNumber;
    private String username; // Still included for update
}
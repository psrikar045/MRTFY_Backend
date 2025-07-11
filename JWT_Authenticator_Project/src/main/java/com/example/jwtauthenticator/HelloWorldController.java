package com.example.jwtauthenticator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Hello World", description = "Basic hello world endpoint for testing")
public class HelloWorldController {

    @GetMapping("/hello")
    @Operation(
        summary = "Hello World", 
        description = "Returns a simple hello world message"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully returned hello message")
    })
    public String hello() {
        return "Hello, World!";
    }
}

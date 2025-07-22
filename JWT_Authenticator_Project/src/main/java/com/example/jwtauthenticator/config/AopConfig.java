package com.example.jwtauthenticator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration to enable AspectJ AOP for scope-based method security.
 */
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
    // This configuration enables the @RequireApiKeyScope annotation processing
}
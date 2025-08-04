package com.example.jwtauthenticator.exception;

/**
 * Exception thrown when domain validation fails
 */
public class DomainValidationException extends RuntimeException {
    
    private final String errorCode;
    private final String domain;
    
    public DomainValidationException(String message) {
        super(message);
        this.errorCode = "DOMAIN_VALIDATION_FAILED";
        this.domain = null;
    }
    
    public DomainValidationException(String message, String domain) {
        super(message);
        this.errorCode = "DOMAIN_VALIDATION_FAILED";
        this.domain = domain;
    }
    
    public DomainValidationException(String message, String errorCode, String domain) {
        super(message);
        this.errorCode = errorCode;
        this.domain = domain;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getDomain() {
        return domain;
    }
}
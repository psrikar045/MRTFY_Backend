package com.example.jwtauthenticator.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    public void logEvent(String username, String eventType, String details) {
        logger.info("AUDIT | {} | User: {} | Event: {} | Details: {}", LocalDateTime.now(), username, eventType, details);
        // In a real application, this would write to a database, log file, or message queue
    }
}

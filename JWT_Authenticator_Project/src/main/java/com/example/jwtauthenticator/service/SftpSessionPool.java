package com.example.jwtauthenticator.service;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pool of SFTP sessions to avoid creating a new connection for each file upload
 */
@Component
@Slf4j
public class SftpSessionPool {

    @Value("${app.file-storage.remote.host:202.65.155.125}")
    private String remoteHost;
    
    @Value("${app.file-storage.remote.port:22}")
    private int remotePort;
    
    @Value("${app.file-storage.remote.username}")
    private String remoteUsername;
    
    @Value("${app.file-storage.remote.password}")
    private String remotePassword;
    
    @Value("${app.file-storage.remote.timeout-seconds:30}")
    private int timeoutSeconds;
    
    @Value("${app.file-storage.remote.pool.min-size:2}")
    private int minPoolSize;
    
    @Value("${app.file-storage.remote.pool.max-size:5}")
    private int maxPoolSize;
    
    private final ConcurrentLinkedQueue<PooledSession> sessionPool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeSessionCount = new AtomicInteger(0);
    private volatile boolean shuttingDown = false;
    
    @PostConstruct
    public void init() {
        // Initialize JSch logger only if it hasn't been set already
        try {
            JSch.setLogger(new com.jcraft.jsch.Logger() {
                @Override
                public boolean isEnabled(int level) {
                    return log.isDebugEnabled(); // Only log if debug is enabled to reduce noise
                }
                
                @Override
                public void log(int level, String message) {
                    switch (level) {
                        case INFO:
                            log.debug("JSch INFO: {}", message);
                            break;
                        case WARN:
                            log.warn("JSch WARN: {}", message);
                            break;
                        case ERROR:
                            log.error("JSch ERROR: {}", message);
                            break;
                        case DEBUG:
                            log.debug("JSch DEBUG: {}", message);
                            break;
                        case FATAL:
                            log.error("JSch FATAL: {}", message);
                            break;
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Failed to set JSch logger: {}", e.getMessage());
        }
        
        // Initialize pool with minimum number of sessions
        try {
            int successfulConnections = 0;
            for (int i = 0; i < minPoolSize; i++) {
                try {
                    PooledSession session = createSession();
                    if (session != null) {
                        sessionPool.offer(session);
                        successfulConnections++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to create initial SFTP session {}: {}", i + 1, e.getMessage());
                }
            }
            log.info("SFTP session pool initialized with {}/{} connections", successfulConnections, minPoolSize);
        } catch (Exception e) {
            log.error("Failed to initialize SFTP session pool: {}", e.getMessage(), e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        log.info("Shutting down SFTP session pool...");
        
        // Close all sessions in the pool
        PooledSession session;
        while ((session = sessionPool.poll()) != null) {
            closeSession(session);
        }
        
        log.info("SFTP session pool shutdown complete");
    }
    
    /**
     * Get current pool status for monitoring
     */
    public String getPoolStatus() {
        return String.format("SFTP Pool - Available: %d, Active: %d, Total: %d (Min: %d, Max: %d)", 
                sessionPool.size(), 
                activeSessionCount.get(),
                sessionPool.size() + activeSessionCount.get(),
                minPoolSize, 
                maxPoolSize);
    }
    
    /**
     * Get a session from the pool or create a new one if needed
     */
    public PooledSession getSession() throws JSchException {
        if (shuttingDown) {
            throw new IllegalStateException("SFTP session pool is shutting down");
        }
        
        // Try to get a session from the pool
        PooledSession session = sessionPool.poll();
        
        // If no session is available, create a new one if below max size
        if (session == null) {
            int currentCount = activeSessionCount.get();
            if (currentCount < maxPoolSize) {
                session = createSession();
                if (session != null) {
                    activeSessionCount.incrementAndGet();
                    log.debug("Created new SFTP session, active count: {}", activeSessionCount.get());
                } else {
                    throw new JSchException("Failed to create SFTP session");
                }
            } else {
                log.warn("Maximum SFTP session pool size reached ({}), waiting for available session", maxPoolSize);
                // Wait for a session to become available
                try {
                    Thread.sleep(500);
                    return getSession(); // Recursive call, but with delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new JSchException("Interrupted while waiting for SFTP session");
                }
            }
        } else {
            // Check if the session is still valid
            try {
                if (!session.isValid() || !session.session.isConnected()) {
                    log.info("Reconnecting stale SFTP session");
                    closeSession(session);
                    session = createSession();
                    if (session == null) {
                        throw new JSchException("Failed to recreate SFTP session");
                    }
                } else {
                    log.debug("Reusing existing SFTP session from pool");
                }
            } catch (Exception e) {
                log.warn("Error checking session state, creating new session: {}", e.getMessage());
                closeSession(session);
                session = createSession();
                if (session == null) {
                    throw new JSchException("Failed to recreate SFTP session after error");
                }
            }
        }
        
        return session;
    }
    
    /**
     * Return a session to the pool
     */
    public void returnSession(PooledSession session) {
        if (session == null) {
            return;
        }
        
        if (shuttingDown) {
            closeSession(session);
            activeSessionCount.decrementAndGet();
            return;
        }
        
        try {
            // Check if session is still valid
            if (session.isValid() && session.session.isConnected()) {
                // Close the SFTP channel but keep the session alive for reuse
                session.closeChannel();
                sessionPool.offer(session);
                log.debug("Returned SFTP session to pool, pool size: {}", sessionPool.size());
            } else {
                log.info("Discarding disconnected SFTP session");
                closeSession(session);
                activeSessionCount.decrementAndGet();
                
                // Create a replacement session if below min size
                int totalSessions = sessionPool.size() + activeSessionCount.get();
                if (totalSessions < minPoolSize) {
                    try {
                        PooledSession newSession = createSession();
                        if (newSession != null) {
                            sessionPool.offer(newSession);
                            log.debug("Created replacement SFTP session, pool size: {}", sessionPool.size());
                        }
                    } catch (Exception e) {
                        log.error("Failed to create replacement SFTP session: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error returning session to pool: {}", e.getMessage());
            closeSession(session);
            activeSessionCount.decrementAndGet();
        }
    }
    
    /**
     * Create a new SFTP session
     */
    private PooledSession createSession() throws JSchException {
        try {
            log.debug("Creating new SFTP session to {}:{}", remoteHost, remotePort);
            
            // Create JSch instance
            JSch jsch = new JSch();
            
            // Create session
            Session session = jsch.getSession(remoteUsername, remoteHost, remotePort);
            session.setPassword(remotePassword);
            
            // Configure session properties
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "password");
            config.put("compression.s2c", "zlib,none");
            config.put("compression.c2s", "zlib,none");
            session.setConfig(config);
            
            // Set timeout
            session.setTimeout(timeoutSeconds * 1000);
            
            // Connect
            session.connect();
            log.debug("SFTP session connected to {}:{}", remoteHost, remotePort);
            
            // Create and return pooled session
            return new PooledSession(session);
        } catch (JSchException e) {
            log.error("Failed to create SFTP session: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Close a session
     */
    private void closeSession(PooledSession pooledSession) {
        if (pooledSession == null) {
            return;
        }
        
        try {
            // Close the channel first
            pooledSession.closeChannel();
            
            // Then close the session
            if (pooledSession.session != null && pooledSession.session.isConnected()) {
                pooledSession.session.disconnect();
            }
        } catch (Exception e) {
            log.warn("Error closing SFTP session: {}", e.getMessage());
        }
    }
    
    /**
     * Wrapper class for a pooled SFTP session
     */
    public static class PooledSession {
        public final Session session; // Made public for access in pool
        private Channel channel;
        private ChannelSftp channelSftp;
        
        public PooledSession(Session session) {
            this.session = session;
        }
        
        /**
         * Get the SFTP channel, creating it if needed
         */
        public ChannelSftp getSftpChannel() throws JSchException {
            if (channelSftp == null || !channelSftp.isConnected()) {
                // Close existing channel if it exists but is not connected
                if (channel != null && channel.isConnected()) {
                    channel.disconnect();
                }
                
                // Ensure session is still connected
                if (!session.isConnected()) {
                    throw new JSchException("Session is not connected");
                }
                
                // Open new SFTP channel
                channel = session.openChannel("sftp");
                channel.connect();
                channelSftp = (ChannelSftp) channel;
            }
            
            return channelSftp;
        }
        
        /**
         * Check if this pooled session is valid and connected
         */
        public boolean isValid() {
            try {
                return session != null && session.isConnected();
            } catch (Exception e) {
                return false;
            }
        }
        
        /**
         * Close the SFTP channel but keep the session alive for reuse
         */
        public void closeChannel() {
            try {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
                if (channel != null && channel.isConnected()) {
                    channel.disconnect();
                }
                channelSftp = null;
                channel = null;
            } catch (Exception e) {
                // Ignore errors during cleanup
            }
        }
    }
}
package com.example.jwtauthenticator.service;

import com.jcraft.jsch.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for storing files on a remote server using SFTP
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SftpFileStorageService {

    private final SftpSessionPool sessionPool;
    
    @Value("${app.file-storage.remote.base-path:/home/ubuntu/images/Brand_Assets}")
    private String remoteBasePath;
    
    @Value("${app.file-storage.local.base-path:./Brand_Assets}")
    private String localBasePath;
    
    /**
     * Upload a file to the remote server using SFTP
     * 
     * @param fileContent The file content as byte array
     * @param targetPath The target path on the remote server
     * @return The path where the file was stored
     * @throws IOException If an I/O error occurs
     */
    public String uploadFile(byte[] fileContent, String targetPath) throws IOException {
        SftpSessionPool.PooledSession pooledSession = null;
        
        try {
            log.info("Uploading file to remote server via SFTP: {}, size: {} bytes", targetPath, fileContent.length);
            
            // Get a session from the pool
            pooledSession = sessionPool.getSession();
            ChannelSftp channelSftp = pooledSession.getSftpChannel();
            
            // Create full remote path
            String fullRemotePath = remoteBasePath + "/" + targetPath;
            log.info("Full remote path: {}", fullRemotePath);
            
            // Create parent directories if they don't exist
            try {
                createRemoteDirectories(channelSftp, fullRemotePath);
            } catch (Exception e) {
                log.error("Error creating remote directories: {}", e.getMessage(), e);
                throw e;
            }
            
            // Upload file
            try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
                channelSftp.put(inputStream, fullRemotePath);
            }
            
            log.info("Successfully uploaded file to remote server: {}", fullRemotePath);
            return targetPath;
            
        } catch (JSchException | SftpException e) {
            log.error("Error uploading file to remote server: {}", e.getMessage(), e);
            
            // Fall back to local storage if remote upload fails
            log.info("Falling back to local storage for file: {}", targetPath);
            return storeFileLocally(fileContent, targetPath);
        } finally {
            // Return the session to the pool
            if (pooledSession != null) {
                sessionPool.returnSession(pooledSession);
            }
        }
    }
    
    /**
     * Create parent directories on the remote server
     */
    private void createRemoteDirectories(ChannelSftp channelSftp, String fullPath) throws SftpException {
        // Get parent directory path
        String parentPath = fullPath.substring(0, fullPath.lastIndexOf('/'));
        
        // Try to change to the directory to check if it exists
        try {
            channelSftp.cd(parentPath);
            return; // Directory exists, nothing to do
        } catch (SftpException e) {
            // Directory doesn't exist, create it
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                // Create parent directories recursively
                createRemoteDirectories(channelSftp, parentPath);
                
                // Create this directory
                log.debug("Creating remote directory: {}", parentPath);
                channelSftp.mkdir(parentPath);
            } else {
                throw e; // Other error, rethrow
            }
        }
    }
    
    /**
     * Store file in local filesystem as fallback
     */
    private String storeFileLocally(byte[] content, String targetPath) throws IOException {
        Path fullPath = Paths.get(localBasePath, targetPath);
        
        // Create directories if they don't exist
        Files.createDirectories(fullPath.getParent());
        
        // Check if file already exists
        boolean fileExists = Files.exists(fullPath);
        if (fileExists) {
            log.info("File already exists locally, overwriting: {}", fullPath);
        }
        
        // Write file (this will overwrite if it exists)
        Files.write(fullPath, content);
        
        log.info("Successfully stored file locally: {} (Size: {} bytes)", fullPath, content.length);
        
        // Return relative path for database storage
        return targetPath;
    }
}
# SFTP File Storage Configuration

This document explains how to set up the SFTP file storage system for brand assets and images.

## Overview

The application now supports storing files directly on the remote server at `/images/Brand_Assets` using SFTP. This allows files to be stored directly on the server's file system instead of locally.

## Configuration

### 1. Application Properties

The following properties in `application.properties` control the file storage behavior:

```properties
# File Storage Configuration
app.file-storage.type=sftp                                      # Options: local, sftp, s3, gcs
app.file-storage.local.base-path=./Brand_Assets                 # Local fallback path
app.file-storage.server.base-url=http://202.65.155.125:8080/images/Brand_Assets  # Base URL for accessing files

# SFTP Configuration
app.file-storage.remote.host=202.65.155.125                     # SFTP server hostname
app.file-storage.remote.port=22                                 # SFTP port (default: 22)
app.file-storage.remote.username=your-username                  # SFTP username
app.file-storage.remote.password=your-password                  # SFTP password
app.file-storage.remote.base-path=/images/Brand_Assets          # Base path on remote server
app.file-storage.remote.timeout-seconds=30                      # Connection timeout
```

### 2. Remote Server Setup

The remote server (202.65.155.125) needs to:

1. Have an SSH/SFTP server running
2. Have a user account with the configured username and password
3. Have write permissions to the directory `/images/Brand_Assets`
4. Serve files from that directory via HTTP

## How It Works

1. When a brand asset or image needs to be stored:
   - The application downloads the file from the original URL
   - It then uploads the file to the remote server using SFTP
   - The file is stored directly in the specified path on the server

2. If the SFTP upload fails:
   - The application falls back to storing the file locally
   - This ensures the system continues to work even if the remote server is unavailable

3. The database stores the relative path to the file:
   - Example: `brands/2/assets/logo_wipro-logo-new-og-502x263.jpg`
   - This path is combined with the base URL to create the full URL

## Dependencies

The SFTP functionality requires the JSch library, which is included in the project dependencies:

```xml
<dependency>
    <groupId>com.jcraft</groupId>
    <artifactId>jsch</artifactId>
    <version>0.1.55</version>
</dependency>
```

## Deployment Instructions

### On the Remote Server (202.65.155.125)

1. Ensure SSH/SFTP server is running:
   ```
   systemctl status ssh
   ```

2. Create the directory structure:
   ```
   mkdir -p /images/Brand_Assets
   ```

3. Set appropriate permissions:
   ```
   chmod -R 755 /images
   chown -R your-username:your-group /images
   ```

4. Configure the web server (Apache/Nginx) to serve files from this directory

### On the Application Server

1. Set `app.file-storage.type=sftp` in `application.properties`
2. Configure the SFTP connection details (username, password, etc.)
3. Restart the application

## Testing

To test if the SFTP storage is working:

1. Trigger a brand extraction
2. Check the logs for messages like:
   ```
   Successfully stored file via SFTP: brands/2/assets/logo_wipro-logo-new-og-502x263.jpg (Size: 12345 bytes)
   ```

3. Verify the file exists on the remote server:
   ```
   ls -la /images/Brand_Assets/brands/2/assets/
   ```

4. Access the file via HTTP:
   ```
   http://202.65.155.125:8080/images/Brand_Assets/brands/2/assets/logo_wipro-logo-new-og-502x263.jpg
   ```

## Troubleshooting

If files are not being stored via SFTP:

1. Check application logs for upload errors
2. Verify the SFTP server is accessible:
   ```
   sftp your-username@202.65.155.125
   ```
3. Check permissions on the remote directory
4. Verify the SFTP credentials are correct

If the application falls back to local storage, you'll see messages like:
```
Failed to store file via SFTP, falling back to local storage: Auth fail
```

## Security Considerations

1. **Credentials**: Store SFTP credentials securely, preferably in environment variables or a secure vault
2. **SSH Keys**: Consider using SSH key authentication instead of passwords
3. **Restricted Access**: Configure the SFTP user with limited permissions (chroot jail)
4. **Firewall**: Restrict SSH/SFTP access to trusted IP addresses
# Remote File Storage Configuration

This document explains how to set up the remote file storage system for brand assets and images.

## Overview

The application now supports storing files on a remote server via HTTP. This allows files to be stored directly on the server at `http://202.65.155.125:8080/images/Brand_Assets` instead of locally.

## Configuration

### 1. Application Properties

The following properties in `application.properties` control the file storage behavior:

```properties
# File Storage Configuration
app.file-storage.type=remote                                      # Options: local, remote, s3, gcs
app.file-storage.local.base-path=./Brand_Assets                   # Local fallback path
app.file-storage.server.base-url=http://202.65.155.125:8080/images/Brand_Assets  # Base URL for accessing files
app.file-storage.remote.upload-url=http://202.65.155.125:8080/images/upload      # Upload endpoint
app.file-storage.remote.auth-token=                               # Optional auth token
app.file-storage.remote.timeout-seconds=30                        # Upload timeout
```

### 2. Remote Server Setup

The remote server (202.65.155.125) needs to:

1. Have the `FileUploadController` deployed and accessible at `/images/upload`
2. Have write permissions to the directory `/var/www/html/images/Brand_Assets`
3. Serve files from that directory via HTTP

## How It Works

1. When a brand asset or image needs to be stored:
   - The application downloads the file from the original URL
   - It then uploads the file to the remote server using WebClient
   - The remote server stores the file and returns the URL

2. If the remote upload fails:
   - The application falls back to storing the file locally
   - This ensures the system continues to work even if the remote server is unavailable

3. The database stores the relative path to the file:
   - Example: `brands/2/assets/logo_wipro-logo-new-og-502x263.jpg`
   - This path is combined with the base URL to create the full URL

## Deployment Instructions

### On the Remote Server (202.65.155.125)

1. Create the directory structure:
   ```
   mkdir -p /var/www/html/images/Brand_Assets
   ```

2. Set appropriate permissions:
   ```
   chmod -R 755 /var/www/html/images
   chown -R www-data:www-data /var/www/html/images
   ```

3. Configure the web server (Apache/Nginx) to serve files from this directory

4. Deploy the `FileUploadController` to handle file uploads

### On the Application Server

1. Set `app.file-storage.type=remote` in `application.properties`
2. Ensure the application can reach the remote server
3. Restart the application

## Testing

To test if the remote storage is working:

1. Trigger a brand extraction
2. Check the logs for messages like:
   ```
   Successfully stored file remotely: brands/2/assets/logo_wipro-logo-new-og-502x263.jpg (Size: 12345 bytes)
   ```

3. Verify the file exists on the remote server:
   ```
   ls -la /var/www/html/images/Brand_Assets/brands/2/assets/
   ```

4. Access the file via HTTP:
   ```
   http://202.65.155.125:8080/images/Brand_Assets/brands/2/assets/logo_wipro-logo-new-og-502x263.jpg
   ```

## Troubleshooting

If files are not being stored remotely:

1. Check application logs for upload errors
2. Verify the remote server is accessible
3. Check permissions on the remote directory
4. Verify the upload endpoint is working correctly

If the application falls back to local storage, you'll see messages like:
```
Failed to store file remotely, falling back to local storage: Connection refused
```
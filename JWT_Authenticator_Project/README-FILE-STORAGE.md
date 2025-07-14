# File Storage Configuration Guide

This document explains how to configure and use the different file storage options available in the application.

## Available Storage Options

The application supports multiple storage methods for brand assets and images:

1. **Local Storage** - Files are stored on the local file system
2. **HTTP Upload** - Files are uploaded to a remote server via HTTP
3. **SFTP** - Files are transferred to a remote server via SFTP
4. **S3** - Files are stored in Amazon S3 (not fully implemented)
5. **GCS** - Files are stored in Google Cloud Storage (not fully implemented)

## Configuration

### Basic Configuration

In `application.properties`, set the storage type:

```properties
# Choose one: local, http, sftp, s3, gcs
app.file-storage.type=local
```

### Local Storage Configuration

```properties
# Local storage path
app.file-storage.local.base-path=D:/Brand_Assets
```

### HTTP Upload Configuration

```properties
# Remote server URL for HTTP uploads
app.file-storage.server.base-url=http://202.65.155.125:8080/images/Brand_Assets
app.file-storage.remote.upload-url=http://202.65.155.125:8080/images/upload
app.file-storage.remote.auth-token=your-auth-token
app.file-storage.remote.timeout-seconds=30
```

### SFTP Configuration

```properties
# SFTP server details
app.file-storage.remote.host=202.65.155.125
app.file-storage.remote.port=22
app.file-storage.remote.username=your-username
app.file-storage.remote.password=your-password
app.file-storage.remote.base-path=/images/Brand_Assets
```

## How Each Storage Method Works

### 1. Local Storage

Files are stored directly on the local file system at the path specified in `app.file-storage.local.base-path`. This is the simplest option and requires no additional configuration.

Example:
- Configuration: `app.file-storage.local.base-path=D:/Brand_Assets`
- File path: `brands/1/logo.png`
- Stored at: `D:/Brand_Assets/brands/1/logo.png`

### 2. HTTP Upload

Files are uploaded to a remote server using HTTP multipart form data. This requires a server endpoint that can accept file uploads.

#### How HTTP Upload Works:

1. The application creates a multipart form request with:
   - The file content as a file part
   - The target path as a form field

2. The request is sent to the URL specified in `app.file-storage.remote.upload-url`

3. The remote server processes the upload and stores the file

4. The server should return a success response

#### Remote Server Requirements:

The remote server must have an endpoint (e.g., `http://202.65.155.125:8080/images/upload`) that:

1. Accepts POST requests with multipart/form-data content type
2. Processes two parts:
   - `file`: The file content
   - `path`: The target path where the file should be stored
3. Stores the file at the specified path
4. Returns a success response

#### Example Server Implementation (Spring Boot):

```java
@RestController
@RequestMapping("/images")
public class FileUploadController {

    @Value("${file.upload.base-path:/images/Brand_Assets}")
    private String uploadBasePath;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String path) {
        
        try {
            // Create full path
            Path targetPath = Paths.get(uploadBasePath, path);
            
            // Create directories if they don't exist
            Files.createDirectories(targetPath.getParent());
            
            // Save the file
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            return ResponseEntity.ok("File uploaded successfully to: " + path);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload file: " + e.getMessage());
        }
    }
}
```

### 3. SFTP Storage

Files are transferred to a remote server using the Secure File Transfer Protocol (SFTP). This method provides secure, direct access to the remote file system.

#### How SFTP Works:

1. The application maintains a pool of SFTP connections to the remote server
2. When a file needs to be uploaded, it gets a connection from the pool
3. It creates any necessary directories on the remote server
4. It uploads the file to the specified path
5. The connection is returned to the pool for reuse

#### Connection Pooling:

The application uses a connection pool for SFTP to improve performance:
- Maintains a minimum number of persistent connections (default: 2)
- Creates additional connections as needed up to a maximum (default: 5)
- Reuses connections for multiple file uploads
- Automatically reconnects if a connection is lost

#### Requirements:

1. SSH/SFTP server running on the remote host
2. Valid username and password (or key-based authentication)
3. Appropriate permissions to write to the target directory

#### Configuration:

```properties
# SFTP pool configuration
app.file-storage.remote.pool.min-size=2
app.file-storage.remote.pool.max-size=5
```

## Fallback Mechanism

All remote storage methods (HTTP, SFTP, etc.) include a fallback to local storage. If the remote storage fails for any reason, the file will be stored locally to ensure it's not lost.

## Accessing Stored Files

Files stored using any method can be accessed via HTTP using the URL pattern:

```
{app.file-storage.server.base-url}/{relative-path}
```

For example:
- Base URL: `http://202.65.155.125:8080/images/Brand_Assets`
- Relative path: `brands/1/logo.png`
- Full URL: `http://202.65.155.125:8080/images/Brand_Assets/brands/1/logo.png`

## Troubleshooting

### HTTP Upload Issues

If files aren't being uploaded via HTTP:

1. Check application logs for error messages
2. Verify the upload URL is correct and accessible
3. Ensure the remote server has an endpoint that can handle file uploads
4. Check if authentication is required (set `app.file-storage.remote.auth-token`)
5. Increase the timeout if needed (`app.file-storage.remote.timeout-seconds`)

### SFTP Issues

If SFTP uploads are failing:

1. Verify the SFTP server is running and accessible
2. Check the username and password are correct
3. Ensure the user has write permissions to the target directory
4. Try connecting manually using an SFTP client

### Local Storage Issues

If local storage is failing:

1. Ensure the application has write permissions to the directory
2. Check if the disk has sufficient space
3. Verify the path exists or can be created
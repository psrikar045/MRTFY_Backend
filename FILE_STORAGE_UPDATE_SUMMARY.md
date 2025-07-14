# File Storage Update Summary

## Overview
Updated the file storage system to use server URLs (`http://202.65.155.125:8080/images`) instead of local file paths for accessing downloaded brand assets and images.

## Changes Made

### 1. FileStorageService.java
- **Added server base URL configuration**: `app.file-storage.server.base-url`
- **Updated `storeFileLocally()` method**: Now returns relative path instead of full local path
- **Added URL construction methods**:
  - `getFileUrl(String relativePath)`: Constructs full server URL from relative path
  - `getAssetUrl(BrandAsset asset)`: Gets server URL for brand assets
  - `getImageUrl(BrandImage image)`: Gets server URL for brand images
- **Updated `getFileAsResource()` method**: Converts relative path to full local path for file access
- **Enhanced logging**: Shows both relative path and server URL in download logs

### 2. application.properties
- **Updated configuration**:
  - `app.file-storage.local.base-path=./Brand_Assets` (local file system path)
  - `app.file-storage.server.base-url=http://202.65.155.125:8080/images/Brand_Assets` (server URL for access)

### 3. BrandDataService.java
- **Added FileStorageService dependency**
- **Updated `convertAssets()` method**: Uses `fileStorageService.getAssetUrl()` for downloaded files
- **Updated `convertImages()` method**: Uses `fileStorageService.getImageUrl()` for downloaded files
- **Fallback logic**: Uses original URLs for files that haven't been downloaded yet

## How It Works

### File Storage Process
1. **Download**: Files are downloaded and stored locally in `./Brand_Assets/brands/{brandId}/assets/` or `./Brand_Assets/brands/{brandId}/images/`
2. **Database Storage**: Only the relative path is stored in the database (e.g., `brands/1/assets/logo_example.png`)
3. **URL Generation**: When serving data via API, the full server URL is constructed (e.g., `http://202.65.155.125:8080/images/Brand_Assets/brands/1/assets/logo_example.png`)

### API Response Example
```json
{
  "id": 1,
  "name": "Example Brand",
  "assets": [
    {
      "id": 1,
      "assetType": "LOGO",
      "originalUrl": "https://example.com/logo.png",
      "accessUrl": "http://202.65.155.125:8080/images/Brand_Assets/brands/1/assets/logo_example.png",
      "fileName": "logo_example.png",
      "downloadStatus": "COMPLETED"
    }
  ],
  "images": [
    {
      "id": 1,
      "sourceUrl": "https://example.com/image.jpg",
      "accessUrl": "http://202.65.155.125:8080/images/Brand_Assets/brands/1/images/image_example.jpg",
      "fileName": "image_example.jpg",
      "downloadStatus": "COMPLETED"
    }
  ]
}
```

## Configuration Options

### Environment Variables
You can override the server URL using environment variables or different application profiles:

```properties
# For production
app.file-storage.server.base-url=http://202.65.155.125:8080/images/Brand_Assets

# For development
app.file-storage.server.base-url=http://localhost:8080/images/Brand_Assets

# For staging
app.file-storage.server.base-url=http://staging.example.com:8080/images/Brand_Assets
```

## Benefits
1. **Flexible Deployment**: Easy to change server URLs without database migration
2. **Efficient Storage**: Only relative paths stored in database
3. **Fallback Support**: Original URLs used when files aren't downloaded yet
4. **Environment Agnostic**: Same code works across different environments
5. **Future Proof**: Ready for cloud storage implementations (S3, GCS)

## File Serving
The existing endpoints in BrandController continue to work:
- `/api/brands/assets/{assetId}` - Serves asset files
- `/api/brands/images/{imageId}` - Serves image files

These endpoints serve the actual files from the local storage, while the `accessUrl` in API responses provides direct HTTP access to the files.
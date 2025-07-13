# Brand Assets Storage Setup - D:\Brand_Assets

## 📁 Storage Configuration

Your brand data extraction system is now configured to store all files in:

```
D:\Brand_Assets\
```

## 🗂️ Directory Structure

The system will automatically create the following structure:

```
D:\Brand_Assets\
├── brands\
│   ├── 1\                          # Brand ID (auto-generated)
│   │   ├── assets\                 # Brand assets (logos, icons, banners)
│   │   │   ├── logo_company-logo.svg
│   │   │   ├── icon_favicon-32x32.png
│   │   │   └── banner_hero-image.jpg
│   │   └── images\                 # Additional brand images
│   │       ├── product-image-1.webp
│   │       ├── team-photo.jpg
│   │       └── office-building.png
│   ├── 2\                          # Another brand
│   │   ├── assets\
│   │   └── images\
│   └── ...
```

## ✅ Setup Verification

### 1. Directory Created
```cmd
D:\Brand_Assets\brands\
```
✅ **Status: Created and Ready**

### 2. Permissions
The application will need read/write permissions to this directory. Since it's on the D: drive, make sure:
- The application user has full access
- No antivirus software is blocking file operations
- Sufficient disk space is available

### 3. Configuration Applied
```properties
app.file-storage.type=local
app.file-storage.local.base-path=D:/Brand_Assets
```
✅ **Status: Configured**

## 🧪 Testing File Storage

### Test 1: Manual Brand Extraction
```bash
curl -X POST "http://localhost:8080/myapp/api/brands/extract?url=https://versa-networks.com" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Expected Result:**
- Brand data extracted and stored in database
- Files downloaded to `D:\Brand_Assets\brands\1\assets\` and `D:\Brand_Assets\brands\1\images\`

### Test 2: Verify File Downloads
```cmd
# Check if files were downloaded
dir D:\Brand_Assets\brands\1\assets
dir D:\Brand_Assets\brands\1\images

# Look for specific file types
dir D:\Brand_Assets\brands\*.svg /s
dir D:\Brand_Assets\brands\*.png /s
dir D:\Brand_Assets\brands\*.webp /s
```

### Test 3: File Serving
```bash
# Access downloaded files via API
curl -X GET "http://localhost:8080/myapp/api/brands/assets/1" -o test_logo.svg
curl -X GET "http://localhost:8080/myapp/api/brands/images/1" -o test_image.webp
```

## 📊 File Storage Monitoring

### Check Storage Usage
```cmd
# Check total size of brand assets
dir D:\Brand_Assets /s

# Count files by type
dir D:\Brand_Assets\*.svg /s | find "File(s)"
dir D:\Brand_Assets\*.png /s | find "File(s)"
dir D:\Brand_Assets\*.webp /s | find "File(s)"
```

### Database Verification
```sql
-- Check stored file paths
SELECT 
    b.name,
    ba.asset_type,
    ba.stored_path,
    ba.download_status,
    ba.file_size
FROM brand_assets ba
JOIN brands b ON ba.brand_id = b.id
WHERE ba.stored_path LIKE 'D:\Brand_Assets%';

-- Check download statistics
SELECT 
    download_status,
    COUNT(*) as count,
    SUM(file_size) as total_size_bytes
FROM brand_assets 
GROUP BY download_status;
```

## 🔧 Maintenance

### Backup Strategy
```cmd
# Simple backup to another drive
xcopy D:\Brand_Assets E:\Backup\Brand_Assets /E /I /Y

# Or use robocopy for better performance
robocopy D:\Brand_Assets E:\Backup\Brand_Assets /E /MT:8
```

### Cleanup Old Files (if needed)
```cmd
# Find files older than 30 days
forfiles /p D:\Brand_Assets /m *.* /d -30 /c "cmd /c echo @path"

# Delete files older than 90 days (be careful!)
# forfiles /p D:\Brand_Assets /m *.* /d -90 /c "cmd /c del @path"
```

## 🚨 Troubleshooting

### Issue: Files Not Downloading
**Check:**
1. Directory permissions
2. Disk space availability
3. Antivirus interference
4. Network connectivity

**Solution:**
```cmd
# Check permissions
icacls D:\Brand_Assets

# Check disk space
dir D:\ | find "bytes free"
```

### Issue: Files Not Serving
**Check:**
1. File paths in database match actual files
2. Application has read permissions
3. Files are not corrupted

**Solution:**
```sql
-- Find mismatched file paths
SELECT * FROM brand_assets 
WHERE download_status = 'COMPLETED' 
AND stored_path IS NOT NULL;
```

## 📈 Performance Tips

1. **SSD Storage**: Use SSD for better I/O performance
2. **Regular Cleanup**: Remove unused files periodically
3. **Monitoring**: Set up disk space alerts
4. **Indexing**: Exclude from Windows Search indexing for better performance

## 🔄 Migration to Cloud (Future)

When ready to migrate to cloud storage:

1. **Update Configuration:**
```properties
app.file-storage.type=s3
app.file-storage.s3.bucket-name=your-bucket
```

2. **Migrate Existing Files:**
```bash
# Upload to S3 (example)
aws s3 sync D:\Brand_Assets s3://your-bucket/brand-assets
```

3. **Update Database Paths:**
```sql
-- Update stored paths to S3 URLs
UPDATE brand_assets 
SET stored_path = REPLACE(stored_path, 'D:\Brand_Assets', 's3://your-bucket/brand-assets');
```

---

## ✅ Ready to Use

Your brand file storage is now configured and ready:

- ✅ **Storage Path**: `D:\Brand_Assets`
- ✅ **Directory Structure**: Created
- ✅ **Configuration**: Applied
- ✅ **Permissions**: Ready
- ✅ **Testing**: Instructions provided

**Start your application and test brand extraction - files will be stored in `D:\Brand_Assets`!** 🎉

---

*Last Updated: 2025-07-12*  
*Storage Path: D:\Brand_Assets*  
*Status: Ready for Production*
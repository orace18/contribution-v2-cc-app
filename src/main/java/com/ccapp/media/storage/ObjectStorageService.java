package com.ccapp.media.storage;

import java.time.Duration;

public interface ObjectStorageService {

    PresignedUrl createUploadUrl(String bucket, String objectKey, String contentType, Duration ttl);

    PresignedUrl createDownloadUrl(String bucket, String objectKey, Duration ttl);

    boolean objectExists(String bucket, String objectKey);
}

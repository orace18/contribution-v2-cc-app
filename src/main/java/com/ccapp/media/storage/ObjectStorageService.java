package com.ccapp.media.storage;

import java.time.Duration;

public interface ObjectStorageService {

    PresignedUrl createUploadUrl(String bucket, String objectKey, String contentType, Duration ttl);

    PresignedUrl createDownloadUrl(String bucket, String objectKey, Duration ttl);

    boolean objectExists(String bucket, String objectKey);

    /** Upload raw bytes directly (used as proxy when presigned URL is not reachable by the client). */
    void putObject(String bucket, String objectKey, byte[] data, String contentType);
}

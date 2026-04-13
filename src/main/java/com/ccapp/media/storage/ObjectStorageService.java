package com.ccapp.media.storage;

import java.time.Duration;

public interface ObjectStorageService {

    PresignedUrl createUploadUrl(String bucket, String objectKey, String contentType, Duration ttl);

    PresignedUrl createDownloadUrl(String bucket, String objectKey, Duration ttl);

    String publicObjectUrl(String bucket, String objectKey);

    boolean objectExists(String bucket, String objectKey);

    /** Upload raw bytes directly (used as proxy when presigned URL is not reachable by the client). */
    void putObject(String bucket, String objectKey, byte[] data, String contentType);

    /** Read raw bytes directly from the backing storage without going through a public URL. */
    byte[] readObject(String bucket, String objectKey);
}

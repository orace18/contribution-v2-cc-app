package com.ccapp.media.storage;

import com.ccapp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
public class NoopMediaStorageService implements ObjectStorageService {

    private static final String MEDIA_STORAGE_UNAVAILABLE = "Media storage is temporarily unavailable";

    private final MediaStorageModeDecision decision;

    @Override
    public PresignedUrl createUploadUrl(String bucket, String objectKey, String contentType, Duration ttl) {
        throw unavailable("createUploadUrl", bucket, objectKey);
    }

    @Override
    public PresignedUrl createDownloadUrl(String bucket, String objectKey, Duration ttl) {
        throw unavailable("createDownloadUrl", bucket, objectKey);
    }

    @Override
    public boolean objectExists(String bucket, String objectKey) {
        throw unavailable("objectExists", bucket, objectKey);
    }

    @Override
    public void putObject(String bucket, String objectKey, byte[] data, String contentType) {
        throw unavailable("putObject", bucket, objectKey);
    }

    private BusinessException unavailable(String operation, String bucket, String objectKey) {
        log.warn(
                "Media storage noop fallback rejected operation={} endpoint={} bucket={} key={} reason={}",
                operation,
                MediaStorageConfig.defaultIfBlank(decision.endpoint(), "<unset>"),
                bucket,
                objectKey,
                decision.reason()
        );
        return new BusinessException(MEDIA_STORAGE_UNAVAILABLE);
    }
}

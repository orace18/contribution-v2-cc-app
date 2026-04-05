package com.ccapp.media.storage;

public record MediaStorageModeDecision(
        MediaStorageMode mode,
        String reason,
        String endpoint
) {

    public boolean usesMinioStorage() {
        return mode == MediaStorageMode.MINIO;
    }

    public boolean usesLocalDevStorage() {
        return mode == MediaStorageMode.LOCAL_DEV;
    }

    public boolean usesNoopStorage() {
        return mode == MediaStorageMode.NOOP;
    }
}

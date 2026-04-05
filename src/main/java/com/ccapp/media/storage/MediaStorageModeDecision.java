package com.ccapp.media.storage;

public record MediaStorageModeDecision(
        MediaStorageMode mode,
        String reason,
        String endpoint
) {

    public boolean usesLocalDevStorage() {
        return mode == MediaStorageMode.LOCAL_DEV;
    }
}

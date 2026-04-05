package com.ccapp.media.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDevStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createUploadUrlAndPutPublicObject_shareTheSameFileId() throws Exception {
        LocalDevStorageService service = new LocalDevStorageService(
                tempDir.toString(),
                "http://localhost:8080/cc-backend-monolitic",
                "http://localhost:8080/cc-backend-monolitic"
        );

        String bucket = "cc-media-kyc";
        String objectKey = "account-holders/test/KYC_IDENTITY_FRONT/123e4567-e89b-12d3-a456-426614174000-passport.jpg";
        PresignedUrl uploadUrl = service.createUploadUrl(bucket, objectKey, "image/jpeg", Duration.ofMinutes(15));
        String fileId = uploadUrl.url().substring(uploadUrl.url().lastIndexOf('/') + 1);

        service.putPublicObject(fileId, "passport".getBytes(), "image/jpeg");

        assertThat(service.objectExists(bucket, objectKey)).isTrue();
        assertThat(service.createDownloadUrl(bucket, objectKey, Duration.ofMinutes(15)).url())
                .isEqualTo(uploadUrl.url());

        LocalDevStorageService.StoredLocalMediaFile storedFile = service.loadPublicObject(fileId);
        assertThat(storedFile.contentType()).isEqualTo("image/jpeg");
        assertThat(Files.readString(storedFile.path())).isEqualTo("passport");
    }

    @Test
    void putObjectStoresBytesUsingObjectKeyDerivedUuid() throws Exception {
        LocalDevStorageService service = new LocalDevStorageService(
                tempDir.toString(),
                "http://localhost:8080/cc-backend-monolitic",
                "http://localhost:8080/cc-backend-monolitic"
        );

        String bucket = "cc-media-kyc";
        String objectKey = "account-holders/test/KYC_SELFIE/123e4567-e89b-12d3-a456-426614174001-selfie.jpg";

        service.putObject(bucket, objectKey, "selfie".getBytes(), "image/jpeg");

        String downloadUrl = service.createDownloadUrl(bucket, objectKey, Duration.ofMinutes(15)).url();
        String fileId = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);

        LocalDevStorageService.StoredLocalMediaFile storedFile = service.loadPublicObject(fileId);
        assertThat(storedFile.contentType()).isEqualTo("image/jpeg");
        assertThat(Files.readString(storedFile.path())).isEqualTo("selfie");
    }
}

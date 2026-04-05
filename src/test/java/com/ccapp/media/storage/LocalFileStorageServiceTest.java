package com.ccapp.media.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createUploadUrlAndPutPublicObject_shareTheSameFileId() throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(
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

        LocalFileStorageService.StoredLocalMediaFile storedFile = service.loadPublicObject(fileId);
        assertThat(storedFile.contentType()).isEqualTo("image/jpeg");
        assertThat(Files.readString(storedFile.path())).isEqualTo("passport");
    }

    @Test
    void putObjectStoresBytesUsingObjectKeyDerivedUuid() throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(
                tempDir.toString(),
                "http://localhost:8080/cc-backend-monolitic",
                "http://localhost:8080/cc-backend-monolitic"
        );

        String bucket = "cc-media-kyc";
        String objectKey = "account-holders/test/KYC_SELFIE/123e4567-e89b-12d3-a456-426614174001-selfie.jpg";

        service.putObject(bucket, objectKey, "selfie".getBytes(), "image/jpeg");

        String downloadUrl = service.createDownloadUrl(bucket, objectKey, Duration.ofMinutes(15)).url();
        String fileId = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);

        LocalFileStorageService.StoredLocalMediaFile storedFile = service.loadPublicObject(fileId);
        assertThat(storedFile.contentType()).isEqualTo("image/jpeg");
        assertThat(Files.readString(storedFile.path())).isEqualTo("selfie");
    }

    @Test
    void createUploadUrl_usesForwardedProtoWhenPresent() {
        LocalFileStorageService service = new LocalFileStorageService(
                tempDir.toString(),
                "",
                "https://localhost:8080/cc-backend-monolitic"
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("api-gateway.clubchretien.org");
        request.setServerPort(80);
        request.setContextPath("/cc-backend-monolitic");
        request.addHeader("Host", "api-gateway.clubchretien.org");
        request.addHeader("X-Forwarded-Proto", "https");

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            PresignedUrl uploadUrl = service.createUploadUrl(
                    "cc-media-public",
                    "account-holders/test/PROFILE_PHOTO/123e4567-e89b-12d3-a456-426614174002-avatar.jpg",
                    "image/jpeg",
                    Duration.ofMinutes(15)
            );

            assertThat(uploadUrl.url())
                    .startsWith("https://api-gateway.clubchretien.org/cc-backend-monolitic/dev-media/");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void createUploadUrl_defaultsToHttpsWhenForwardedProtoIsMissing() {
        LocalFileStorageService service = new LocalFileStorageService(
                tempDir.toString(),
                "",
                "https://localhost:8080/cc-backend-monolitic"
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("api-gateway.clubchretien.org");
        request.setServerPort(80);
        request.setContextPath("/cc-backend-monolitic");
        request.addHeader("Host", "api-gateway.clubchretien.org");

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            PresignedUrl uploadUrl = service.createUploadUrl(
                    "cc-media-public",
                    "account-holders/test/PROFILE_PHOTO/123e4567-e89b-12d3-a456-426614174003-avatar.jpg",
                    "image/jpeg",
                    Duration.ofMinutes(15)
            );

            assertThat(uploadUrl.url())
                    .startsWith("https://api-gateway.clubchretien.org/cc-backend-monolitic/dev-media/");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}

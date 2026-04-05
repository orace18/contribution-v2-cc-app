package com.ccapp.media.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaStorageConfigTest {

    private final MediaStorageConfig config = new MediaStorageConfig();

    @Test
    void mediaStorageSettings_resolvesRuntimeEndpoints() {
        MediaStorageSettings settings = config.mediaStorageSettings(
                "http://localhost:9000",
                "https://cdn.example.com",
                "us-east-1",
                "minio",
                "secret",
                true
        );

        assertThat(settings.endpoint()).isEqualTo("http://localhost:9000");
        assertThat(settings.endpointUri()).hasToString("http://localhost:9000");
        assertThat(settings.publicEndpoint()).isEqualTo("https://cdn.example.com");
        assertThat(settings.presignerEndpointUri()).hasToString("https://cdn.example.com");
        assertThat(settings.region()).isEqualTo("us-east-1");
        assertThat(settings.pathStyle()).isTrue();
    }

    @Test
    void mediaStorageSettings_usesInternalEndpointWhenPublicEndpointIsBlank() {
        MediaStorageSettings settings = config.mediaStorageSettings(
                "http://localhost:9000",
                "   ",
                "us-east-1",
                "minio",
                "secret",
                true
        );

        assertThat(settings.publicEndpoint()).isNull();
        assertThat(settings.presignerEndpointUri()).isEqualTo(settings.endpointUri());
    }

    @Test
    void mediaStorageModeResolver_usesLocalFileFallbackWhenEndpointIsMissing() {
        MediaStorageModeDecision decision = MediaStorageModeResolver.resolve(
                null,
                250
        );

        assertThat(decision.mode()).isEqualTo(MediaStorageMode.LOCAL_FILE);
        assertThat(decision.reason()).contains("missing");
    }

    @Test
    void mediaStorageModeResolver_usesLocalFileFallbackWhenEndpointIsUnreachable() {
        MediaStorageModeDecision decision = MediaStorageModeResolver.resolve(
                "http://127.0.0.1:6553",
                250
        );

        assertThat(decision.mode()).isEqualTo(MediaStorageMode.LOCAL_FILE);
        assertThat(decision.reason()).contains("not reachable");
    }

    @Test
    void mediaStorageModeResolver_usesLocalFileFallbackWhenEndpointHostIsNotResolvable() {
        MediaStorageModeDecision decision = MediaStorageModeResolver.resolve(
                "http://minio-host-that-should-not-exist.invalid:9000",
                250
        );

        assertThat(decision.mode()).isEqualTo(MediaStorageMode.LOCAL_FILE);
        assertThat(decision.reason()).contains("not resolvable");
    }

    @Test
    void mediaStorageModeResolver_usesMinioWhenEndpointIsReachable() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            MediaStorageModeDecision decision = MediaStorageModeResolver.resolve(
                    "http://127.0.0.1:" + serverSocket.getLocalPort(),
                    250
            );

            assertThat(decision.mode()).isEqualTo(MediaStorageMode.MINIO);
            assertThat(decision.reason()).contains("reachable");
        }
    }

    @Test
    void mediaStorageModeResolver_failsFastWhenStrictStartupIsEnabled() {
        assertThatThrownBy(() -> MediaStorageModeResolver.resolve(
                "http://127.0.0.1:6553",
                250,
                true
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strict startup");
    }
}

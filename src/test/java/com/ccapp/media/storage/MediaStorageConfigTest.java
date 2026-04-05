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
    void mediaStorageModeResolver_usesLocalFallbackWhenDevProfileIsActiveAndEndpointIsMissing() {
        MediaStorageModeDecision decision = MediaStorageModeResolver.resolve(
                new String[]{"dev"},
                null,
                250
        );

        assertThat(decision.mode()).isEqualTo(MediaStorageMode.LOCAL_DEV);
        assertThat(decision.reason()).contains("profile 'dev'");
    }

    @Test
    void mediaStorageModeResolver_usesNoopFallbackWhenDevEndpointIsUnreachable() {
        MediaStorageModeDecision decision = MediaStorageModeResolver.resolve(
                new String[]{"dev"},
                "http://127.0.0.1:6553",
                250
        );

        assertThat(decision.mode()).isEqualTo(MediaStorageMode.NOOP);
        assertThat(decision.reason()).contains("not reachable");
    }

    @Test
    void mediaStorageModeResolver_usesNoopFallbackWhenEndpointIsUnreachable() {
        MediaStorageModeDecision decision = MediaStorageModeResolver.resolve(
                new String[]{"prod"},
                "http://127.0.0.1:6553",
                250
        );

        assertThat(decision.mode()).isEqualTo(MediaStorageMode.NOOP);
        assertThat(decision.reason()).contains("not reachable");
    }

    @Test
    void mediaStorageModeResolver_usesMinioWhenDevEndpointIsReachable() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            MediaStorageModeDecision decision = MediaStorageModeResolver.resolve(
                    new String[]{"dev"},
                    "http://127.0.0.1:" + serverSocket.getLocalPort(),
                    250
            );

            assertThat(decision.mode()).isEqualTo(MediaStorageMode.MINIO);
            assertThat(decision.reason()).contains("reachable");
        }
    }

    @Test
    void mediaStorageModeResolver_keepsMinioWhenEndpointIsReachable() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            MediaStorageModeDecision decision = MediaStorageModeResolver.resolve(
                    new String[]{"prod"},
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
                new String[]{"prod"},
                "http://127.0.0.1:6553",
                250,
                true
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strict startup");
    }
}

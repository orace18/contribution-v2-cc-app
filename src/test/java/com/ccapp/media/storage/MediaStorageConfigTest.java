package com.ccapp.media.storage;

import org.junit.jupiter.api.Test;

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
    void mediaStorageSettings_failsFastWhenEndpointHostCannotBeResolved() {
        assertThatThrownBy(() -> config.mediaStorageSettings(
                "http://media-storage.invalid:9000",
                "",
                "us-east-1",
                "minio",
                "secret",
                true
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("media.storage.endpoint")
                .hasMessageContaining("cannot be resolved")
                .hasMessageContaining("MEDIA_STORAGE_ENDPOINT");
    }
}

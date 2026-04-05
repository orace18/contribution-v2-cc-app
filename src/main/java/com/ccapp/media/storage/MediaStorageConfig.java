package com.ccapp.media.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Slf4j
@Configuration
public class MediaStorageConfig {

    @Bean
    public MediaStorageSettings mediaStorageSettings(@Value("${media.storage.endpoint:http://localhost:9000}") String endpoint,
                                                     @Value("${media.storage.public-endpoint:}") String publicEndpoint,
                                                     @Value("${media.storage.region:us-east-1}") String region,
                                                     @Value("${media.storage.access-key}") String accessKey,
                                                     @Value("${media.storage.secret-key}") String secretKey,
                                                     @Value("${media.storage.path-style:true}") boolean pathStyle) {
        String resolvedEndpoint = requireNonBlank(endpoint, "media.storage.endpoint");
        String resolvedRegion = defaultIfBlank(region, "us-east-1");
        String resolvedAccessKey = requireNonBlank(accessKey, "media.storage.access-key");
        String resolvedSecretKey = requireNonBlank(secretKey, "media.storage.secret-key");
        URI resolvedEndpointUri = parseEndpoint(resolvedEndpoint, "media.storage.endpoint");
        validateResolvableHost(resolvedEndpointUri, "media.storage.endpoint");
        URI presignerEndpointUri = resolvePresignerEndpoint(publicEndpoint, resolvedEndpointUri);

        log.info(
                "Media storage configuration resolved internalEndpoint={} publicEndpoint={} presignerEndpoint={} region={} pathStyle={}",
                resolvedEndpointUri,
                isBlank(publicEndpoint) ? "<same as internal>" : publicEndpoint.trim(),
                presignerEndpointUri,
                resolvedRegion,
                pathStyle
        );

        return new MediaStorageSettings(
                resolvedEndpoint,
                resolvedEndpointUri,
                isBlank(publicEndpoint) ? null : publicEndpoint.trim(),
                presignerEndpointUri,
                resolvedRegion,
                resolvedAccessKey,
                resolvedSecretKey,
                pathStyle
        );
    }

    @Bean
    public S3Client mediaS3Client(MediaStorageSettings settings) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(settings.accessKey(), settings.secretKey());
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(settings.pathStyle())
                .build();
        return S3Client.builder()
                .endpointOverride(settings.endpointUri())
                .region(Region.of(settings.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    @Bean
    public S3Presigner mediaS3Presigner(MediaStorageSettings settings) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(settings.accessKey(), settings.secretKey());
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(settings.pathStyle())
                .build();
        return S3Presigner.builder()
                .endpointOverride(settings.presignerEndpointUri())
                .region(Region.of(settings.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    static URI resolvePresignerEndpoint(String publicEndpoint, URI internalEndpointUri) {
        if (isBlank(publicEndpoint)) {
            return internalEndpointUri;
        }
        return parseEndpoint(publicEndpoint.trim(), "media.storage.public-endpoint");
    }

    static URI parseEndpoint(String value, String propertyName) {
        String normalizedValue = requireNonBlank(value, propertyName);
        URI endpointUri;
        try {
            endpointUri = URI.create(normalizedValue);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid " + propertyName + ": " + normalizedValue, ex);
        }
        if (isBlank(endpointUri.getScheme()) || isBlank(endpointUri.getHost())) {
            throw new IllegalStateException(
                    "Invalid " + propertyName + ": " + normalizedValue
                            + ". Expected an absolute URI like http://minio:9000 or https://media.example.com"
            );
        }
        return endpointUri;
    }

    static void validateResolvableHost(URI endpointUri, String propertyName) {
        try {
            InetAddress.getAllByName(endpointUri.getHost());
        } catch (UnknownHostException ex) {
            throw new IllegalStateException(
                    "Invalid " + propertyName + "=" + endpointUri
                            + ": hostname '" + endpointUri.getHost()
                            + "' cannot be resolved from the backend runtime. "
                            + "Configure MEDIA_STORAGE_ENDPOINT with a host reachable by the backend container.",
                    ex
            );
        }
    }

    static String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    static String requireNonBlank(String value, String propertyName) {
        if (isBlank(value)) {
            throw new IllegalStateException("Missing required property: " + propertyName);
        }
        return value.trim();
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

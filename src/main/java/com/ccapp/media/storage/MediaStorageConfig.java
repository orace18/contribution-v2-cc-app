package com.ccapp.media.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class MediaStorageConfig {

    @Bean
    public S3Client mediaS3Client(@Value("${media.storage.endpoint:http://localhost:9000}") String endpoint,
                                  @Value("${media.storage.region:us-east-1}") String region,
                                  @Value("${media.storage.access-key}") String accessKey,
                                  @Value("${media.storage.secret-key}") String secretKey,
                                  @Value("${media.storage.path-style:true}") boolean pathStyle) {
        String resolvedEndpoint = defaultIfBlank(endpoint, "http://localhost:9000");
        String resolvedRegion = defaultIfBlank(region, "us-east-1");
        String resolvedAccessKey = requireNonBlank(accessKey, "media.storage.access-key");
        String resolvedSecretKey = requireNonBlank(secretKey, "media.storage.secret-key");

        AwsBasicCredentials credentials = AwsBasicCredentials.create(resolvedAccessKey, resolvedSecretKey);
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .build();
        return S3Client.builder()
                .endpointOverride(URI.create(resolvedEndpoint))
                .region(Region.of(resolvedRegion))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    @Bean
    public S3Presigner mediaS3Presigner(@Value("${media.storage.endpoint:http://localhost:9000}") String endpoint,
                                        @Value("${media.storage.region:us-east-1}") String region,
                                        @Value("${media.storage.access-key}") String accessKey,
                                        @Value("${media.storage.secret-key}") String secretKey,
                                        @Value("${media.storage.path-style:true}") boolean pathStyle) {
        String resolvedEndpoint = defaultIfBlank(endpoint, "http://localhost:9000");
        String resolvedRegion = defaultIfBlank(region, "us-east-1");
        String resolvedAccessKey = requireNonBlank(accessKey, "media.storage.access-key");
        String resolvedSecretKey = requireNonBlank(secretKey, "media.storage.secret-key");

        AwsBasicCredentials credentials = AwsBasicCredentials.create(resolvedAccessKey, resolvedSecretKey);
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .build();
        return S3Presigner.builder()
                .endpointOverride(URI.create(resolvedEndpoint))
                .region(Region.of(resolvedRegion))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String requireNonBlank(String value, String propertyName) {
        if (isBlank(value)) {
            throw new IllegalStateException("Missing required secret: " + propertyName);
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

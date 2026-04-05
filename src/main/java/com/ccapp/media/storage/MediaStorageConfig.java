package com.ccapp.media.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Slf4j
@Configuration
public class MediaStorageConfig {

    @Bean
    public MediaStorageModeDecision mediaStorageModeDecision(Environment environment) {
        MediaStorageModeDecision decision = MediaStorageModeResolver.resolve(environment);
        if (decision.usesLocalDevStorage()) {
            log.warn(
                    "Local dev media storage fallback enabled mode={} endpoint={} reason={}",
                    decision.mode(),
                    defaultIfBlank(decision.endpoint(), "<unset>"),
                    decision.reason()
            );
        } else if (decision.usesNoopStorage()) {
            log.warn(
                    "Noop media storage fallback enabled mode={} endpoint={} reason={}",
                    decision.mode(),
                    defaultIfBlank(decision.endpoint(), "<unset>"),
                    decision.reason()
            );
        } else {
            log.info(
                    "Media storage configured mode={} endpoint={} reason={}",
                    decision.mode(),
                    defaultIfBlank(decision.endpoint(), "<unset>"),
                    decision.reason()
            );
        }
        return decision;
    }

    @Bean
    @Conditional(UseMinioStorageCondition.class)
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
    @Conditional(UseMinioStorageCondition.class)
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
    @Conditional(UseMinioStorageCondition.class)
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

    @Bean
    @Conditional(UseMinioStorageCondition.class)
    public ObjectStorageService minioObjectStorageService(S3Client mediaS3Client,
                                                          S3Presigner mediaS3Presigner,
                                                          MediaStorageSettings settings) {
        return new MinioObjectStorageService(mediaS3Client, mediaS3Presigner, settings);
    }

    @Bean
    @Conditional(UseLocalDevStorageCondition.class)
    public LocalDevStorageService localDevStorageService(
            MediaStorageModeDecision decision,
            @Value("${media.local-dev.root-path:/tmp/ccapp-media/}") String rootPath,
            @Value("${media.local-dev.public-base-url:}") String configuredPublicBaseUrl,
            @Value("${server.port:8080}") int serverPort,
            @Value("${server.servlet.context-path:}") String contextPath) {
        String fallbackBaseUrl = "http://localhost:" + serverPort + normalizeContextPath(contextPath);
        log.info(
                "Using local dev media storage rootPath={} configuredPublicBaseUrl={} fallbackPublicBaseUrl={} reason={}",
                rootPath,
                isBlank(configuredPublicBaseUrl) ? "<request-derived>" : configuredPublicBaseUrl.trim(),
                fallbackBaseUrl,
                decision.reason()
        );
        return new LocalDevStorageService(rootPath, configuredPublicBaseUrl, fallbackBaseUrl);
    }

    @Bean
    @Conditional(UseLocalDevStorageCondition.class)
    public ObjectStorageService localDevObjectStorageService(LocalDevStorageService localDevStorageService) {
        return localDevStorageService;
    }

    @Bean
    @Conditional(UseNoopStorageCondition.class)
    public ObjectStorageService noopObjectStorageService(MediaStorageModeDecision decision) {
        return new NoopMediaStorageService(decision);
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

    static String normalizeContextPath(String contextPath) {
        if (isBlank(contextPath)) {
            return "";
        }
        String trimmed = contextPath.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}

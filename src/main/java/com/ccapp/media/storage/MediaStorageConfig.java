package com.ccapp.media.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.nio.file.Path;

@Slf4j
@Configuration
public class MediaStorageConfig {

    @Bean
    public LocalFileStorageService localFileStorageService(
            @Value("${media.local-file.root-path:./dev-storage/}") String rootPath,
            @Value("${media.local-file.public-base-url:}") String configuredPublicBaseUrl,
            @Value("${server.port:8080}") int serverPort,
            @Value("${server.servlet.context-path:}") String contextPath) {
        String fallbackBaseUrl = "https://localhost:" + serverPort + normalizeContextPath(contextPath);
        return new LocalFileStorageService(rootPath, configuredPublicBaseUrl, fallbackBaseUrl);
    }

    @Bean
    public MediaStorageModeDecision mediaStorageModeDecision(Environment environment,
                                                             @Value("${media.storage.endpoint:http://localhost:9000}") String endpoint,
                                                             @Value("${media.storage.public-endpoint:}") String publicEndpoint,
                                                             @Value("${media.storage.region:us-east-1}") String region,
                                                             @Value("${media.storage.access-key:}") String accessKey,
                                                             @Value("${media.storage.secret-key:}") String secretKey,
                                                             @Value("${media.storage.path-style:true}") boolean pathStyle,
                                                             @Value("${media.bucket.public:cc-media-public}") String publicBucket,
                                                             @Value("${media.storage.startup-strict:false}") boolean strictStartup,
                                                             @Value("${media.local-file.root-path:./dev-storage/}") String localRootPath) {
        MediaStorageModeDecision decision = MediaStorageModeResolver.resolve(environment);
        if (decision.usesLocalFileStorage()) {
            logLocalFallback(decision, localRootPath);
            return decision;
        }

        try {
            MediaStorageSettings settings = mediaStorageSettings(
                    endpoint,
                    publicEndpoint,
                    region,
                    accessKey,
                    secretKey,
                    pathStyle
            );
            probeMinioAvailability(settings, publicBucket);
            log.info(
                    "Media storage configured mode={} endpoint={} reason={}",
                    decision.mode(),
                    defaultIfBlank(decision.endpoint(), "<unset>"),
                    decision.reason()
            );
            return decision;
        } catch (RuntimeException ex) {
            if (strictStartup) {
                throw ex;
            }
            MediaStorageModeDecision fallbackDecision = new MediaStorageModeDecision(
                    MediaStorageMode.LOCAL_FILE,
                    "MinIO client initialization failed: " + rootCauseMessage(ex),
                    endpoint
            );
            logLocalFallback(fallbackDecision, localRootPath);
            return fallbackDecision;
        }
    }

    @Bean(destroyMethod = "close")
    public ObjectStorageService objectStorageService(MediaStorageModeDecision decision,
                                                     LocalFileStorageService localFileStorageService,
                                                     @Value("${media.storage.endpoint:http://localhost:9000}") String endpoint,
                                                     @Value("${media.storage.public-endpoint:}") String publicEndpoint,
                                                     @Value("${media.storage.region:us-east-1}") String region,
                                                     @Value("${media.storage.access-key:}") String accessKey,
                                                     @Value("${media.storage.secret-key:}") String secretKey,
                                                     @Value("${media.storage.path-style:true}") boolean pathStyle) {
        if (decision.usesLocalFileStorage()) {
            return localFileStorageService;
        }

        MediaStorageSettings settings = mediaStorageSettings(
                endpoint,
                publicEndpoint,
                region,
                accessKey,
                secretKey,
                pathStyle
        );
        return new MinioObjectStorageService(
                createS3Client(settings),
                createS3Presigner(settings),
                settings
        );
    }

    public MediaStorageSettings mediaStorageSettings(String endpoint,
                                                     String publicEndpoint,
                                                     String region,
                                                     String accessKey,
                                                     String secretKey,
                                                     boolean pathStyle) {
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

    static S3Client createS3Client(MediaStorageSettings settings) {
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

    static S3Presigner createS3Presigner(MediaStorageSettings settings) {
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

    private void probeMinioAvailability(MediaStorageSettings settings, String publicBucket) {
        try (S3Client probeClient = createS3Client(settings)) {
            probeClient.headBucket(HeadBucketRequest.builder().bucket(publicBucket).build());
        } catch (NoSuchBucketException ex) {
            log.info(
                    "MinIO connectivity probe reached endpoint={} bucket={} but bucket does not exist yet",
                    settings.endpoint(),
                    publicBucket
            );
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                log.info(
                        "MinIO connectivity probe reached endpoint={} bucket={} and received 404",
                        settings.endpoint(),
                        publicBucket
                );
                return;
            }
            throw ex;
        }
    }

    private void logLocalFallback(MediaStorageModeDecision decision, String localRootPath) {
        log.warn(
                "MinIO unavailable, using local storage fallback endpoint={} reason={} rootPath={}",
                defaultIfBlank(decision.endpoint(), "<unset>"),
                decision.reason(),
                rootPathForLogging(localRootPath)
        );
    }

    private String rootPathForLogging(String rootPath) {
        return Path.of(defaultIfBlank(rootPath, "./dev-storage/")).toAbsolutePath().normalize().toString();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return defaultIfBlank(current.getMessage(), current.getClass().getSimpleName());
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

package com.ccapp.media.storage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaBucketInitializer {

    private final ObjectStorageService objectStorageService;

    @Value("${media.bucket.public:cc-media-public}")
    private String publicBucket;

    @Value("${media.bucket.private:cc-media-private}")
    private String privateBucket;

    @Value("${media.bucket.kyc:cc-media-kyc}")
    private String kycBucket;

    @Value("${media.bucket.auto-create:true}")
    private boolean autoCreateBuckets;

    @Value("${media.storage.startup-strict:false}")
    private boolean strictStartup;

    @PostConstruct
    public void ensureBuckets() {
        if (!(objectStorageService instanceof MinioObjectStorageService minioObjectStorageService)) {
            log.info("Skipping MinIO bucket initialization because local storage fallback is active");
            return;
        }
        if (!autoCreateBuckets) {
            log.info("Media bucket auto-creation disabled");
            return;
        }
        S3Client mediaS3Client = minioObjectStorageService.mediaS3Client();
        for (String bucket : List.of(publicBucket, privateBucket, kycBucket)) {
            try {
                ensureBucketExists(mediaS3Client, bucket);
            } catch (RuntimeException ex) {
                if (strictStartup) {
                    throw ex;
                }
                log.warn("Skipping media bucket initialization for {}: {}", bucket, ex.getMessage());
            }
        }
    }

    private void ensureBucketExists(S3Client mediaS3Client, String bucket) {
        try {
            mediaS3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return;
        } catch (NoSuchBucketException ex) {
            log.info("Media bucket {} not found. Creating it.", bucket);
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw ex;
            }
        }

        mediaS3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        log.info("Media bucket {} created", bucket);
    }
}

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

    private final S3Client mediaS3Client;

    @Value("${media.bucket.public:cc-media-public}")
    private String publicBucket;

    @Value("${media.bucket.private:cc-media-private}")
    private String privateBucket;

    @Value("${media.bucket.kyc:cc-media-kyc}")
    private String kycBucket;

    @PostConstruct
    public void ensureBuckets() {
        for (String bucket : List.of(publicBucket, privateBucket, kycBucket)) {
            ensureBucketExists(bucket);
        }
    }

    private void ensureBucketExists(String bucket) {
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

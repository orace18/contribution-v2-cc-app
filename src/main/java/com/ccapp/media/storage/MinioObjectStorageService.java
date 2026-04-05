package com.ccapp.media.storage;

import com.ccapp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import software.amazon.awssdk.core.sync.RequestBody;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@RequiredArgsConstructor
public class MinioObjectStorageService implements ObjectStorageService {

    private final S3Client mediaS3Client;
    private final S3Presigner mediaS3Presigner;
    private final MediaStorageSettings mediaStorageSettings;

    @Override
    public PresignedUrl createUploadUrl(String bucket, String objectKey, String contentType, Duration ttl) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .putObjectRequest(putObjectRequest)
                    .build();
            PresignedPutObjectRequest presigned = mediaS3Presigner.presignPutObject(presignRequest);
            return new PresignedUrl(
                    presigned.url().toString(),
                    OffsetDateTime.ofInstant(presigned.expiration(), ZoneOffset.UTC)
            );
        } catch (RuntimeException ex) {
            throw new BusinessException("Unable to generate media upload URL");
        }
    }

    @Override
    public PresignedUrl createDownloadUrl(String bucket, String objectKey, Duration ttl) {
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(builder -> builder.bucket(bucket).key(objectKey))
                    .build();
            PresignedGetObjectRequest presigned = mediaS3Presigner.presignGetObject(presignRequest);
            return new PresignedUrl(
                    presigned.url().toString(),
                    OffsetDateTime.ofInstant(presigned.expiration(), ZoneOffset.UTC)
            );
        } catch (RuntimeException ex) {
            throw new BusinessException("Unable to generate media download URL");
        }
    }

    @Override
    public void putObject(String bucket, String objectKey, byte[] data, String contentType) {
        try {
            log.info(
                    "Attempting media storage upload endpoint={} bucket={} key={} bytes={} contentType={}",
                    mediaStorageSettings.endpoint(),
                    bucket,
                    objectKey,
                    data.length,
                    contentType
            );
            doPutObject(bucket, objectKey, data, contentType);
        } catch (NoSuchBucketException ex) {
            log.warn(
                    "putObject bucket not found endpoint={} bucket={}, creating it lazily",
                    mediaStorageSettings.endpoint(),
                    bucket
            );
            mediaS3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            try {
                doPutObject(bucket, objectKey, data, contentType);
            } catch (RuntimeException retryEx) {
                log.error(
                        "putObject retry failed endpoint={} bucket={} key={}: {}",
                        mediaStorageSettings.endpoint(),
                        bucket,
                        objectKey,
                        retryEx.getMessage(),
                        retryEx
                );
                throw new BusinessException("Unable to store media file");
            }
        } catch (RuntimeException ex) {
            log.error(
                    "putObject failed endpoint={} bucket={} key={}: {}",
                    mediaStorageSettings.endpoint(),
                    bucket,
                    objectKey,
                    ex.getMessage(),
                    ex
            );
            throw new BusinessException("Unable to store media file");
        }
    }

    private void doPutObject(String bucket, String objectKey, byte[] data, String contentType) {
        mediaS3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .contentLength((long) data.length)
                        .build(),
                RequestBody.fromBytes(data)
        );
    }

    @Override
    public boolean objectExists(String bucket, String objectKey) {
        try {
            mediaS3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw new BusinessException("Unable to verify uploaded media");
        }
    }
}

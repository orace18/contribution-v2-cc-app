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
                                  @Value("${media.storage.access-key:minioadmin}") String accessKey,
                                  @Value("${media.storage.secret-key:minioadmin}") String secretKey,
                                  @Value("${media.storage.path-style:true}") boolean pathStyle) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .build();
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    @Bean
    public S3Presigner mediaS3Presigner(@Value("${media.storage.endpoint:http://localhost:9000}") String endpoint,
                                        @Value("${media.storage.region:us-east-1}") String region,
                                        @Value("${media.storage.access-key:minioadmin}") String accessKey,
                                        @Value("${media.storage.secret-key:minioadmin}") String secretKey,
                                        @Value("${media.storage.path-style:true}") boolean pathStyle) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .build();
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
    }
}

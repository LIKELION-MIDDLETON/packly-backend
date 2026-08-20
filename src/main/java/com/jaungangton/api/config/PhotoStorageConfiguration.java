package com.jaungangton.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jaungangton.api.analysis.DatabasePhotoStorageAdapter;
import com.jaungangton.api.analysis.MigratingPhotoStorageAdapter;
import com.jaungangton.api.analysis.PhotoStoragePort;
import com.jaungangton.api.analysis.S3PhotoStorageAdapter;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class PhotoStorageConfiguration {
    @Bean
    @ConditionalOnProperty(name = "centralton.photo.storage.mode", havingValue = "database", matchIfMissing = true)
    PhotoStoragePort databasePhotoStoragePort() {
        return new DatabasePhotoStorageAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "centralton.photo.storage.mode", havingValue = "s3")
    S3Client photoS3Client(@Value("${centralton.photo.storage.region:}") String region) {
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("AWS_REGION is required when PHOTO_STORAGE_MODE=s3");
        }
        return S3Client.builder().region(Region.of(region.trim())).build();
    }

    @Bean
    @ConditionalOnProperty(name = "centralton.photo.storage.mode", havingValue = "s3")
    PhotoStoragePort s3PhotoStoragePort(
            S3Client photoS3Client,
            @Value("${centralton.photo.storage.bucket:}") String bucket,
            @Value("${centralton.photo.storage.prefix:}") String prefix) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("S3_PHOTO_BUCKET is required when PHOTO_STORAGE_MODE=s3");
        }
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalStateException("S3_PHOTO_PREFIX is required when PHOTO_STORAGE_MODE=s3");
        }
        return new MigratingPhotoStorageAdapter(new S3PhotoStorageAdapter(photoS3Client, bucket, prefix));
    }
}

package com.jaungangton.api.analysis;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

public class S3PhotoStorageAdapter implements PhotoStoragePort {
    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public S3PhotoStorageAdapter(S3Client s3, String bucket, String prefix) {
        this.s3 = s3;
        this.bucket = requireText(bucket, "S3 photo bucket");
        this.prefix = normalizePrefix(prefix);
    }

    @Override
    public PhotoStorageReference store(UUID analysisId, byte[] data, String contentType) {
        String key = prefix + "/" + analysisId + "/" + UUID.randomUUID();
        PhotoStorageReference reference = PhotoStorageReference.object(key, data, contentType);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) data.length)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .checksumAlgorithm(ChecksumAlgorithm.SHA256)
                .checksumSHA256(nativeChecksumSha256(reference.checksum()))
                .metadata(Map.of("sha256", reference.checksum()))
                .build();
        try {
            s3.putObject(request, RequestBody.fromBytes(data));
            return reference;
        } catch (SdkException exception) {
            deleteBestEffort(key);
            throw new PhotoStorageException("Could not store photo in S3", exception);
        }
    }

    @Override
    public byte[] load(PhotoStorageReference reference) {
        String key = requireObjectKey(reference);
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .checksumMode(ChecksumMode.ENABLED)
                    .build());
            verifyHeadObject(head, reference);

            ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .checksumMode(ChecksumMode.ENABLED)
                    .build());
            byte[] data = response.asByteArray();
            if (data.length != reference.size()
                    || !PhotoStorageReference.sha256(data).equalsIgnoreCase(reference.checksum())) {
                throw new PhotoStorageException("Stored photo integrity check failed");
            }
            return data;
        } catch (PhotoStorageException exception) {
            throw exception;
        } catch (SdkException exception) {
            throw new PhotoStorageException("Could not load photo from S3", exception);
        }
    }

    @Override
    public void delete(PhotoStorageReference reference) {
        String key = requireObjectKey(reference);
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (SdkException exception) {
            throw new PhotoStorageException("Could not delete photo from S3", exception);
        }
    }

    private void deleteBestEffort(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException ignored) {
            // The database has no reference yet; a bucket lifecycle rule is the final fallback.
        }
    }

    private String requireObjectKey(PhotoStorageReference reference) {
        String key = reference == null ? null : reference.objectKey();
        String prefixBoundary = prefix + "/";
        if (key == null || key.isBlank()) {
            throw new PhotoStorageException("S3 photo object key is unavailable");
        }
        if (!key.startsWith(prefixBoundary) || key.length() == prefixBoundary.length()) {
            throw new PhotoStorageException("Photo storage reference is outside the configured prefix");
        }
        return key;
    }

    private void verifyHeadObject(HeadObjectResponse head, PhotoStorageReference reference) {
        Long contentLength = head == null ? null : head.contentLength();
        if (contentLength == null || contentLength != reference.size()) {
            throw new PhotoStorageException("Stored photo integrity check failed");
        }

        String nativeChecksum = head.checksumSHA256();
        if (nativeChecksum != null
                && !nativeChecksum.equals(nativeChecksumSha256(reference.checksum()))) {
            throw new PhotoStorageException("Stored photo integrity check failed");
        }
    }

    private String nativeChecksumSha256(String applicationChecksum) {
        try {
            return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(applicationChecksum));
        } catch (IllegalArgumentException exception) {
            throw new PhotoStorageException("Stored photo integrity check failed");
        }
    }

    private String normalizePrefix(String value) {
        String normalized = requireText(value, "S3 photo prefix").replaceAll("^/+|/+$", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("S3 photo prefix must not be blank");
        }
        return normalized;
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}

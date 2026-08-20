package com.jaungangton.api.analysis;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record PhotoStorageReference(
        byte[] databaseData,
        String objectKey,
        String contentType,
        long size,
        String checksum) {

    public PhotoStorageReference {
        databaseData = databaseData == null ? null : databaseData.clone();
        if ((databaseData == null) == (objectKey == null || objectKey.isBlank())) {
            throw new IllegalArgumentException("Exactly one photo storage location is required");
        }
        if (contentType == null || contentType.isBlank() || size < 0 || checksum == null || checksum.isBlank()) {
            throw new IllegalArgumentException("Photo metadata is incomplete");
        }
    }

    static PhotoStorageReference database(byte[] data, String contentType) {
        return new PhotoStorageReference(data, null, contentType, data.length, sha256(data));
    }

    static PhotoStorageReference object(String objectKey, byte[] data, String contentType) {
        return new PhotoStorageReference(null, objectKey, contentType, data.length, sha256(data));
    }

    static PhotoStorageReference restore(
            byte[] databaseData,
            String objectKey,
            String contentType,
            Long size,
            String checksum) {
        byte[] data = databaseData == null ? null : databaseData.clone();
        long resolvedSize = size == null && data != null ? data.length : size == null ? -1 : size;
        String resolvedChecksum = checksum == null && data != null ? sha256(data) : checksum;
        return new PhotoStorageReference(data, objectKey, contentType, resolvedSize, resolvedChecksum);
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Override
    public byte[] databaseData() {
        return databaseData == null ? null : databaseData.clone();
    }
}

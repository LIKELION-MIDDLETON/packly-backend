package com.jaungangton.api.analysis;

import java.util.UUID;

public class DatabasePhotoStorageAdapter implements PhotoStoragePort {
    @Override
    public PhotoStorageReference store(UUID analysisId, byte[] data, String contentType) {
        return PhotoStorageReference.database(data, contentType);
    }

    @Override
    public byte[] load(PhotoStorageReference reference) {
        byte[] data = reference.databaseData();
        if (data == null) {
            throw new PhotoStorageException("Database photo data is unavailable");
        }
        return data;
    }

    @Override
    public void delete(PhotoStorageReference reference) {
        // Clearing the entity fields deletes database-backed photo data transactionally.
    }
}

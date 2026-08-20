package com.jaungangton.api.analysis;

import java.util.UUID;

/**
 * Writes new photos to object storage while retaining read/delete compatibility
 * for jobs that were created before the S3 cutover.
 */
public class MigratingPhotoStorageAdapter implements PhotoStoragePort {
    private final PhotoStoragePort objectStorage;
    private final PhotoStoragePort legacyDatabaseStorage;

    public MigratingPhotoStorageAdapter(PhotoStoragePort objectStorage) {
        this(objectStorage, new DatabasePhotoStorageAdapter());
    }

    MigratingPhotoStorageAdapter(PhotoStoragePort objectStorage, PhotoStoragePort legacyDatabaseStorage) {
        this.objectStorage = objectStorage;
        this.legacyDatabaseStorage = legacyDatabaseStorage;
    }

    @Override
    public PhotoStorageReference store(UUID analysisId, byte[] data, String contentType) {
        return objectStorage.store(analysisId, data, contentType);
    }

    @Override
    public byte[] load(PhotoStorageReference reference) {
        return delegate(reference).load(reference);
    }

    @Override
    public void delete(PhotoStorageReference reference) {
        delegate(reference).delete(reference);
    }

    private PhotoStoragePort delegate(PhotoStorageReference reference) {
        return reference.databaseData() == null ? objectStorage : legacyDatabaseStorage;
    }
}

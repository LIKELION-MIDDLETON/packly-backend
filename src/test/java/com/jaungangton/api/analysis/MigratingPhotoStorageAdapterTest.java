package com.jaungangton.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class MigratingPhotoStorageAdapterTest {
    @Test
    void writesNewPhotosToObjectStorage() {
        PhotoStoragePort objectStorage = mock(PhotoStoragePort.class);
        PhotoStoragePort databaseStorage = mock(PhotoStoragePort.class);
        UUID analysisId = UUID.randomUUID();
        byte[] data = {1, 2};
        PhotoStorageReference expected = PhotoStorageReference.object(
                "photos/" + analysisId + "/source", data, "image/jpeg");
        when(objectStorage.store(analysisId, data, "image/jpeg")).thenReturn(expected);
        MigratingPhotoStorageAdapter storage = new MigratingPhotoStorageAdapter(objectStorage, databaseStorage);

        assertThat(storage.store(analysisId, data, "image/jpeg")).isEqualTo(expected);
        verify(objectStorage).store(analysisId, data, "image/jpeg");
        verify(databaseStorage, never()).store(analysisId, data, "image/jpeg");
    }

    @Test
    void readsLegacyDatabaseReferencesWithoutCallingS3() {
        PhotoStoragePort objectStorage = mock(PhotoStoragePort.class);
        PhotoStoragePort databaseStorage = mock(PhotoStoragePort.class);
        PhotoStorageReference legacy = PhotoStorageReference.database(new byte[] {3}, "image/png");
        when(databaseStorage.load(legacy)).thenReturn(new byte[] {3});
        MigratingPhotoStorageAdapter storage = new MigratingPhotoStorageAdapter(objectStorage, databaseStorage);

        assertThat(storage.load(legacy)).containsExactly(3);
        verify(databaseStorage).load(legacy);
        verify(objectStorage, never()).load(legacy);
    }

    @Test
    void routesObjectReferencesToS3() {
        PhotoStoragePort objectStorage = mock(PhotoStoragePort.class);
        PhotoStoragePort databaseStorage = mock(PhotoStoragePort.class);
        PhotoStorageReference object = PhotoStorageReference.object(
                "photos/id/source", new byte[] {4}, "image/webp");
        when(objectStorage.load(object)).thenReturn(new byte[] {4});
        MigratingPhotoStorageAdapter storage = new MigratingPhotoStorageAdapter(objectStorage, databaseStorage);

        assertThat(storage.load(object)).containsExactly(4);
        storage.delete(object);

        verify(objectStorage).load(object);
        verify(objectStorage).delete(object);
        verify(databaseStorage, never()).load(object);
        verify(databaseStorage, never()).delete(object);
    }
}

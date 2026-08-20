package com.jaungangton.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DatabasePhotoStorageAdapterTest {
    private final DatabasePhotoStorageAdapter storage = new DatabasePhotoStorageAdapter();

    @Test
    void storesAndLoadsDefensiveDatabaseCopies() {
        byte[] source = {1, 2, 3};

        PhotoStorageReference reference = storage.store(UUID.randomUUID(), source, "image/jpeg");
        source[0] = 9;
        byte[] loaded = storage.load(reference);
        loaded[1] = 9;

        assertThat(storage.load(reference)).containsExactly(1, 2, 3);
        assertThat(reference.objectKey()).isNull();
        assertThat(reference.size()).isEqualTo(3);
        assertThat(reference.checksum()).hasSize(64);
    }
}

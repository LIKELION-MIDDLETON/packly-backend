package com.jaungangton.api.analysis;

import java.util.UUID;

public interface PhotoStoragePort {
    PhotoStorageReference store(UUID analysisId, byte[] data, String contentType);

    byte[] load(PhotoStorageReference reference);

    void delete(PhotoStorageReference reference);
}

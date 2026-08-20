package com.jaungangton.api.analysis;

import java.util.UUID;

public record PhotoAnalysisInput(
        UUID analysisId,
        String surveySnapshot,
        byte[] photoData,
        String contentType) {
    public PhotoAnalysisInput {
        photoData = photoData.clone();
    }

    @Override
    public byte[] photoData() {
        return photoData.clone();
    }
}

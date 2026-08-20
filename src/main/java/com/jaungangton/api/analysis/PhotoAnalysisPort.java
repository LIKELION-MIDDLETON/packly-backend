package com.jaungangton.api.analysis;

import com.jaungangton.api.ai.AiPhotoAnalysisExchange;

public interface PhotoAnalysisPort {
    AiPhotoAnalysisExchange analyze(byte[] image, String contentType, String answersJson, int topK);
}

package com.jaungangton.api.recommendation;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaungangton.api.analysis.AnalysisResultAcceptanceService;

@Service
public class RecommendationFailureService {
    private final AnalysisResultAcceptanceService analysisService;

    public RecommendationFailureService(AnalysisResultAcceptanceService analysisService) {
        this.analysisService = analysisService;
    }

    @Transactional
    public void fail(UUID analysisId, String failureCode) {
        analysisService.markFailed(analysisId, failureCode);
    }
}

package com.jaungangton.api.recommendation;

import java.util.List;

public record RecommendationPageResponse(
        List<RecommendationResultResponse> items,
        String nextCursor) {
}

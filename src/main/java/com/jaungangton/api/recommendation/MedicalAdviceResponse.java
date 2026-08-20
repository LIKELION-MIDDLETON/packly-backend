package com.jaungangton.api.recommendation;

import java.util.List;

public record MedicalAdviceResponse(boolean recommended, List<String> reasons) {
}

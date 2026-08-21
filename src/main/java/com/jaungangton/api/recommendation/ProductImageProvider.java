package com.jaungangton.api.recommendation;

import java.util.Optional;

public interface ProductImageProvider {
    Optional<String> findImageUrl(String brand, String name);
}

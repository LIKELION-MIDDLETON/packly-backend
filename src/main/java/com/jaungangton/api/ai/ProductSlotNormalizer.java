package com.jaungangton.api.ai;

import java.util.Locale;

public final class ProductSlotNormalizer {
    private ProductSlotNormalizer() {
    }

    /** The AI's original slot order is authoritative; slot text is retained verbatim separately. */
    public static Integer applicationOrder(int displayOrder) {
        return displayOrder >= 1 && displayOrder <= 3 ? displayOrder : null;
    }

    public static String usageGroup(int displayOrder, String slot) {
        return switch (displayOrder) {
            case 1, 2, 3 -> "CORE_ROUTINE";
            case 4 -> "CLEANSE";
            case 5 -> "TREATMENT";
            case 6 -> "PROTECT";
            case 7, 8 -> "OCCASIONAL";
            default -> usageGroupFromSlot(slot);
        };
    }

    private static String usageGroupFromSlot(String slot) {
        String value = slot == null ? "" : slot.trim().toLowerCase(Locale.ROOT);
        if (value.contains("클렌") || value.contains("clean") || value.contains("remov")) {
            return "CLEANSE";
        }
        if (value.contains("토너") || value.contains("toner") || value.contains("로션")
                || value.contains("lotion") || value.contains("크림") || value.contains("cream")) {
            return "CORE_ROUTINE";
        }
        if (value.contains("에센스") || value.contains("세럼") || value.contains("essence")
                || value.contains("serum")) {
            return "TREATMENT";
        }
        if (value.contains("선") || value.contains("sun")) {
            return "PROTECT";
        }
        return "OCCASIONAL";
    }
}

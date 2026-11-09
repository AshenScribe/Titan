package com.titan.productservice.util;

public final class VersionHeaderParser {
    private VersionHeaderParser() {}

    public static long parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("If-Match must contain a product version");
        }
        String normalized = value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match must contain a numeric product version");
        }
    }
}

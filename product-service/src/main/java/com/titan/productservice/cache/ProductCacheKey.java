package com.titan.productservice.cache;

import java.util.UUID;

public final class ProductCacheKey {
    private ProductCacheKey() {}

    public static String byId(ProductCacheProperties properties, UUID id) {
        return properties.getNamespace() + ":id:" + id;
    }

    public static String bySlug(ProductCacheProperties properties, String slug) {
        return properties.getNamespace() + ":slug:" + slug.toLowerCase(java.util.Locale.ROOT);
    }

    public static String fence(ProductCacheProperties properties, UUID id) {
        return properties.getNamespace() + ":fence:" + id;
    }

    public static String lookupFence(ProductCacheProperties properties, String cacheKey) {
        return cacheKey + ":fence";
    }

    public static String generation(ProductCacheProperties properties, String cacheKey) {
        return cacheKey + ":generation";
    }
}

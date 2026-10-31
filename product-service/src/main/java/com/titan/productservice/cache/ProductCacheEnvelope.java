package com.titan.productservice.cache;

import java.time.Instant;
import java.util.UUID;

public record ProductCacheEnvelope(
        boolean found,
        ProductSnapshot product,
        UUID productId,
        String fenceKey,
        long version,
        long generation,
        int schemaVersion,
        Instant loadedAt) {
    public static ProductCacheEnvelope found(ProductSnapshot product) {
        return new ProductCacheEnvelope(
                true, product, product.id(), null, product.version(), 0, 1, Instant.now());
    }

    public static ProductCacheEnvelope missing(
            long version, UUID productId, String fenceKey, long generation) {
        return new ProductCacheEnvelope(
                false, null, productId, fenceKey, version, generation, 1, Instant.now());
    }

    public ProductCacheEnvelope withFenceKey(String key) {
        return new ProductCacheEnvelope(
                found, product, productId, key, version, generation, schemaVersion, loadedAt);
    }

    public ProductCacheEnvelope withGeneration(long value) {
        return new ProductCacheEnvelope(
                found, product, productId, fenceKey, version, value, schemaVersion, loadedAt);
    }
}

package com.titan.productservice.cache;

import java.util.UUID;

public record ProductChangedEvent(
        UUID eventId,
        UUID productId,
        String previousSlug,
        String currentSlug,
        long version,
        String eventType) {
    public ProductChangedEvent(
            UUID productId,
            String previousSlug,
            String currentSlug,
            long version,
            String eventType) {
        this(UUID.randomUUID(), productId, previousSlug, currentSlug, version, eventType);
    }
}

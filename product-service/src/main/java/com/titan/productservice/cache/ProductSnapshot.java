package com.titan.productservice.cache;

import com.titan.productservice.data.entity.ProductEntity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductSnapshot(
        UUID id,
        UUID brandId,
        String name,
        String slug,
        String description,
        String status,
        Map<String, Object> metadata,
        long version,
        OffsetDateTime publishedAt,
        OffsetDateTime deletedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ProductSnapshot from(ProductEntity product) {
        return new ProductSnapshot(
                product.getId(),
                product.getBrandId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getStatus(),
                immutableCopy(product.getMetadata()),
                product.getVersion(),
                product.getPublishedAt(),
                product.getDeletedAt(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public ProductEntity toEntity() {
        return new ProductEntity(
                id,
                brandId,
                name,
                slug,
                description,
                status,
                mutableCopy(metadata),
                version,
                publishedAt,
                deletedAt,
                createdAt,
                updatedAt);
    }

    @SuppressWarnings("unchecked")
    private static <T> T mutableCopy(T value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(String.valueOf(key), mutableCopy(nested)));
            return (T) copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(mutableCopy(item)));
            return (T) copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <T> T immutableCopy(T value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(String.valueOf(key), immutableCopy(nested)));
            return (T) Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(immutableCopy(item)));
            return (T) Collections.unmodifiableList(copy);
        }
        return value;
    }
}

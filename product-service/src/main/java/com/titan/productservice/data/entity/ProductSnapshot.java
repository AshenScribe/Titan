//------------------------------------------------------------------------------
// MIT License
//
// Copyright (c) 2026 Rishabh Saraswat
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
//------------------------------------------------------------------------------

package com.titan.productservice.data.entity;

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

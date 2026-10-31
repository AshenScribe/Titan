package com.titan.productservice.presentation.dto;

import com.titan.productservice.data.entity.CategoryEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Schema(name = "CategoryResponse", description = "Category returned by the API.")
public record CategoryResponse(
        UUID id,
        UUID parentId,
        String name,
        String slug,
        String description,
        int sortOrder,
        boolean active,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public static CategoryResponse from(CategoryEntity category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getSortOrder(),
                category.isActive(),
                category.getMetadata(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}

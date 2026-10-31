package com.titan.productservice.presentation.dto;

import com.titan.productservice.data.entity.CategoryEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "ProductCategoryResponse", description = "Category assigned to a product.")
public record ProductCategoryResponse(UUID id, String name, String slug, boolean primary) {
    public static ProductCategoryResponse from(CategoryEntity category, boolean primary) {
        return new ProductCategoryResponse(
                category.getId(), category.getName(), category.getSlug(), primary);
    }
}

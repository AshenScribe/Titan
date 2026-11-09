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

package com.titan.productservice.presentation.dto;

import com.titan.productservice.data.entity.ProductEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Schema(name = "ProductResponse", description = "Product aggregate returned by the admin API.")
public record ProductResponse(
        @Schema(
                        description = "Product identifier.",
                        example = "7f8c2c1e-5f8a-4e8d-9f4e-9b6b6a8b7c10")
                UUID id,
        @Schema(
                        description = "Associated brand identifier.",
                        example = "7f8c2c1e-5f8a-4e8d-9f4e-9b6b6a8b7c10")
                UUID brandId,
        @Schema(description = "Product name.", example = "Classic Oxford Shirt") String name,
        @Schema(description = "Product URL slug.", example = "classic-oxford-shirt") String slug,
        @Schema(description = "Product description.", example = "A durable cotton shirt.")
                String description,
        @Schema(description = "Lifecycle status.", example = "DRAFT") String status,
        @Schema(description = "Product JSON metadata.", example = "{\"season\":\"summer\"}")
                Map<String, Object> metadata,
        @Schema(description = "Aggregate version used with If-Match.", example = "8", minimum = "0")
                long version,
        @Schema(description = "Publication timestamp.", example = "2026-11-01T10:15:30Z")
                OffsetDateTime publishedAt,
        @Schema(description = "Soft-deletion timestamp.", example = "2026-11-01T10:15:30Z")
                OffsetDateTime deletedAt,
        @Schema(description = "Creation timestamp.", example = "2026-11-01T10:15:30Z")
                OffsetDateTime createdAt,
        @Schema(description = "Last update timestamp.", example = "2026-11-01T10:15:30Z")
                OffsetDateTime updatedAt) {
    public static ProductResponse from(ProductEntity product) {
        return new ProductResponse(
                product.getId(),
                product.getBrandId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getStatus(),
                product.getMetadata(),
                product.getVersion(),
                product.getPublishedAt(),
                product.getDeletedAt(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}

// ------------------------------------------------------------------------------
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
// ------------------------------------------------------------------------------

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

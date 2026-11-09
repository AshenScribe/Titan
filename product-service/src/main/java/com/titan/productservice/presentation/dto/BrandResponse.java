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

import com.titan.productservice.data.entity.BrandEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Schema(name = "BrandResponse", description = "Brand returned by the administration API.")
public record BrandResponse(
        @Schema(description = "Brand identifier.", example = "7f8c2c1e-5f8a-4e8d-9f4e-9b6b6a8b7c10")
                UUID id,
        @Schema(description = "Brand display name.", example = "Titan Apparel") String name,
        @Schema(description = "Brand URL slug.", example = "titan-apparel") String slug,
        @Schema(description = "Brand description.", example = "Everyday clothing essentials.")
                String description,
        @Schema(description = "Brand website URL.", example = "https://example.com")
                String websiteUrl,
        @Schema(description = "Brand logo URL.", example = "https://cdn.example.com/logo.svg")
                String logoUrl,
        @Schema(description = "Brand JSON metadata.", example = "{\"country\":\"IN\"}")
                Map<String, Object> metadata,
        @Schema(description = "Whether the brand is active.", example = "true") boolean active,
        @Schema(description = "Creation timestamp.", example = "2026-11-01T10:15:30Z")
                OffsetDateTime createdAt,
        @Schema(description = "Last update timestamp.", example = "2026-11-01T10:15:30Z")
                OffsetDateTime updatedAt) {
    public static BrandResponse from(BrandEntity brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getSlug(),
                brand.getDescription(),
                brand.getWebsiteUrl(),
                brand.getLogoUrl(),
                brand.getMetadata(),
                brand.isActive(),
                brand.getCreatedAt(),
                brand.getUpdatedAt());
    }
}

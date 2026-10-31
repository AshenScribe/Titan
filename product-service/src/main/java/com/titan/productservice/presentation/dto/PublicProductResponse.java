package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(
        name = "PublicProductResponse",
        description = "Published product exposed by the storefront API.")
public record PublicProductResponse(
        UUID id,
        String name,
        String slug,
        String description,
        Map<String, Object> metadata,
        OffsetDateTime publishedAt,
        BrandResponse brand,
        List<CategoryResponse> categories,
        List<PublicVariantResponse> variants,
        List<PublicMediaResponse> media) {}

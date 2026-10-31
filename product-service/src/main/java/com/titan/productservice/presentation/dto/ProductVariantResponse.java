package com.titan.productservice.presentation.dto;

import com.titan.productservice.data.entity.ProductVariantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Schema(
        name = "ProductVariantResponse",
        description = "Administrative product variant representation.")
public record ProductVariantResponse(
        UUID id,
        UUID productId,
        String sku,
        String barcode,
        String name,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String currency,
        Map<String, Object> attributes,
        String status,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public static ProductVariantResponse from(ProductVariantEntity variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getProductId(),
                variant.getSku(),
                variant.getBarcode(),
                variant.getName(),
                variant.getPrice(),
                variant.getCompareAtPrice(),
                variant.getCurrency(),
                variant.getAttributes(),
                variant.getStatus(),
                variant.getVersion(),
                variant.getCreatedAt(),
                variant.getUpdatedAt());
    }
}

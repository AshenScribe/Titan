package com.titan.productservice.presentation.dto;

import com.titan.productservice.data.entity.ProductVariantEntity;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record PublicVariantResponse(
        UUID id,
        String name,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String currency,
        Map<String, Object> attributes) {
    public static PublicVariantResponse from(ProductVariantEntity variant) {
        return new PublicVariantResponse(
                variant.getId(),
                variant.getName(),
                variant.getPrice(),
                variant.getCompareAtPrice(),
                variant.getCurrency(),
                variant.getAttributes());
    }
}

package com.titan.product.orderservice.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProductVariantProvider {
    ProductVariant getActiveVariant(UUID variantId);

    record ProductVariant(
            UUID variantId,
            UUID productId,
            String productName,
            String variantName,
            String sku,
            BigDecimal unitPrice,
            String currency,
            String attributes) {}
}

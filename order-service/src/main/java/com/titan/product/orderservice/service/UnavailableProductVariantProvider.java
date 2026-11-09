package com.titan.product.orderservice.service;

import com.titan.product.orderservice.domain.exception.DependencyUnavailableException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(ProductVariantProvider.class)
public final class UnavailableProductVariantProvider implements ProductVariantProvider {
    @Override
    public ProductVariant getActiveVariant(UUID variantId) {
        throw new DependencyUnavailableException("Product Service is not configured");
    }
}

package com.titan.productservice.presentation.dto;

import com.titan.productservice.data.entity.ProductMediaEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(name = "ProductMediaResponse", description = "Stored media metadata.")
public record ProductMediaResponse(
        UUID id,
        UUID productId,
        String mediaType,
        String storageKey,
        String altText,
        int position,
        boolean primary,
        OffsetDateTime createdAt) {
    public static ProductMediaResponse from(ProductMediaEntity media) {
        return new ProductMediaResponse(
                media.getId(),
                media.getProductId(),
                media.getMediaType(),
                media.getStorageKey(),
                media.getAltText(),
                media.getPosition(),
                media.isPrimary(),
                media.getCreatedAt());
    }
}

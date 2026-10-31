package com.titan.productservice.presentation.dto;

import com.titan.productservice.data.entity.ProductMediaEntity;
import java.util.UUID;

public record PublicMediaResponse(
        UUID id,
        String mediaType,
        String storageKey,
        String altText,
        int position,
        boolean primary) {
    public static PublicMediaResponse from(ProductMediaEntity media) {
        return new PublicMediaResponse(
                media.getId(),
                media.getMediaType(),
                media.getStorageKey(),
                media.getAltText(),
                media.getPosition(),
                media.isPrimary());
    }
}

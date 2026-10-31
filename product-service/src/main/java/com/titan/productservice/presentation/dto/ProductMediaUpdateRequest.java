package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "ProductMediaUpdateRequest", description = "Partial media metadata update.")
public record ProductMediaUpdateRequest(
        @Schema(description = "Media type.", example = "VIDEO") @Pattern(regexp = "IMAGE|VIDEO")
                String mediaType,
        @Schema(description = "Object storage key.", example = "products/7f8c/video-1.mp4")
                @Size(max = 2000)
                String storageKey,
        @Schema(description = "Accessible alternative text.", example = "Shirt detail video")
                @Size(max = 500)
                String altText,
        @Schema(description = "Zero-based display position.", example = "1") @Min(0)
                Integer position,
        @Schema(description = "Whether this is the primary media item.", example = "true")
                Boolean primary) {}

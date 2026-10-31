package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "ProductMediaCreateRequest", description = "Media metadata stored for a product.")
public record ProductMediaCreateRequest(
        @Schema(description = "Media type.", example = "IMAGE")
                @NotBlank
                @Pattern(regexp = "IMAGE|VIDEO")
                String mediaType,
        @Schema(
                        description = "Object storage key; binary upload is handled elsewhere.",
                        example = "products/7f8c/image-1.webp")
                @NotBlank
                @Size(max = 2000)
                String storageKey,
        @Schema(description = "Accessible alternative text.", example = "Blue Oxford shirt")
                @Size(max = 500)
                String altText,
        @Schema(description = "Zero-based display position.", example = "0") @Min(0) int position,
        @Schema(description = "Whether this is the primary media item.", example = "true")
                boolean primary) {}

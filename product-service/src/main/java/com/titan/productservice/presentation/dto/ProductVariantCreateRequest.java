package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

@Schema(
        name = "ProductVariantCreateRequest",
        description = "Fields used to create a product variant.")
public record ProductVariantCreateRequest(
        @Schema(description = "Unique stock keeping unit.", example = "OXFORD-BLUE-M")
                @NotBlank
                @Size(max = 100)
                String sku,
        @Schema(description = "Optional globally unique barcode.", example = "8901234567890")
                @Size(max = 100)
                String barcode,
        @Schema(description = "Variant display name.", example = "Blue / Medium") @Size(max = 255)
                String name,
        @Schema(description = "Variant price.", example = "2499.00")
                @NotNull
                @DecimalMin(value = "0.00")
                BigDecimal price,
        @Schema(
                        description = "Optional original price; must not be below price.",
                        example = "2999.00")
                @DecimalMin(value = "0.00")
                BigDecimal compareAtPrice,
        @Schema(description = "ISO 4217 currency code.", example = "INR")
                @NotBlank
                @Pattern(regexp = "[A-Z]{3}")
                String currency,
        @Schema(
                        description = "Variant attributes.",
                        example = "{\"color\":\"blue\",\"size\":\"M\"}")
                Map<String, Object> attributes) {}

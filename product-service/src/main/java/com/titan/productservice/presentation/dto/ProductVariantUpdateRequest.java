package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

@Schema(name = "ProductVariantUpdateRequest", description = "Partial product variant update.")
public record ProductVariantUpdateRequest(
        @Schema(description = "Unique stock keeping unit.", example = "OXFORD-BLUE-M")
                @Size(max = 100)
                String sku,
        @Schema(description = "Optional globally unique barcode.", example = "8901234567890")
                @Size(max = 100)
                String barcode,
        @Schema(description = "Variant display name.", example = "Blue / Medium") @Size(max = 255)
                String name,
        @Schema(description = "Variant price.", example = "2499.00") @DecimalMin("0.00")
                BigDecimal price,
        @Schema(description = "Original price; must not be below price.", example = "2999.00")
                @DecimalMin("0.00")
                BigDecimal compareAtPrice,
        @Schema(description = "ISO 4217 currency code.", example = "INR")
                @Pattern(regexp = "[A-Z]{3}")
                String currency,
        @Schema(
                        description = "Variant attributes.",
                        example = "{\"color\":\"blue\",\"size\":\"M\"}")
                Map<String, Object> attributes) {}

package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(name = "BrandUpdateRequest", description = "Partial brand update fields.")
public record BrandUpdateRequest(
        @Schema(description = "Replacement brand name.", example = "Titan Apparel")
                @Size(min = 1, max = 150)
                String name,
        @Schema(description = "Replacement lowercase URL slug.", example = "titan-apparel")
                @Size(min = 1, max = 180)
                @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
                String slug,
        @Schema(description = "Replacement description.", example = "Updated description.")
                String description,
        @Schema(description = "Replacement website URL.", example = "https://example.com")
                String websiteUrl,
        @Schema(description = "Replacement logo URL.", example = "https://cdn.example.com/logo.svg")
                String logoUrl,
        @Schema(description = "Replacement JSON metadata.", example = "{\"country\":\"IN\"}")
                Map<String, Object> metadata) {}

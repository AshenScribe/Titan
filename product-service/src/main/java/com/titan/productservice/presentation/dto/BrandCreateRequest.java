package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(name = "BrandCreateRequest", description = "Fields used to create a brand.")
public record BrandCreateRequest(
        @Schema(description = "Brand display name.", example = "Titan Apparel")
                @NotBlank
                @Size(max = 150)
                String name,
        @Schema(description = "Lowercase URL slug.", example = "titan-apparel")
                @NotBlank
                @Size(max = 180)
                @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
                String slug,
        @Schema(description = "Brand description.", example = "Everyday clothing essentials.")
                String description,
        @Schema(description = "Brand website URL.", example = "https://example.com")
                String websiteUrl,
        @Schema(description = "Brand logo URL.", example = "https://cdn.example.com/logo.svg")
                String logoUrl,
        @Schema(description = "Additional JSON metadata.", example = "{\"country\":\"IN\"}")
                Map<String, Object> metadata) {}

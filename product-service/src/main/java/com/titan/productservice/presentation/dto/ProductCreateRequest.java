package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

@Schema(name = "ProductCreateRequest", description = "Fields used to create a product draft.")
public record ProductCreateRequest(
        @Schema(
                        description = "Optional brand identifier.",
                        example = "7f8c2c1e-5f8a-4e8d-9f4e-9b6b6a8b7c10")
                UUID brandId,
        @Schema(description = "Customer-facing product name.", example = "Classic Oxford Shirt")
                @NotBlank
                @Size(max = 255)
                String name,
        @Schema(description = "Lowercase URL slug.", example = "classic-oxford-shirt")
                @NotBlank
                @Size(max = 280)
                @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
                String slug,
        @Schema(description = "Optional product description.", example = "A durable cotton shirt.")
                @Size(max = 10000)
                String description,
        @Schema(description = "Additional JSON metadata.", example = "{\"season\":\"summer\"}")
                Map<String, Object> metadata) {}

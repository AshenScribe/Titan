package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

@Schema(name = "ProductUpdateRequest", description = "Partial product update fields.")
public record ProductUpdateRequest(
        @Schema(
                        description = "Replacement brand identifier.",
                        example = "7f8c2c1e-5f8a-4e8d-9f4e-9b6b6a8b7c10")
                UUID brandId,
        @Schema(description = "Replacement product name.", example = "Classic Oxford Shirt")
                @Size(min = 1, max = 255)
                String name,
        @Schema(description = "Replacement lowercase URL slug.", example = "classic-oxford-shirt")
                @Size(min = 1, max = 280)
                @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
                String slug,
        @Schema(
                        description = "Replacement description.",
                        example = "Updated cotton shirt description.")
                @Size(max = 10000)
                String description,
        @Schema(description = "Replacement JSON metadata.", example = "{\"season\":\"winter\"}")
                Map<String, Object> metadata) {}

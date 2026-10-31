package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

@Schema(name = "CategoryCreateRequest", description = "Fields used to create a category.")
public record CategoryCreateRequest(
        @Schema(
                        description = "Optional parent category.",
                        example = "7f8c2c1e-5f8a-4e8d-9f4e-9b6b6a8b7c10")
                UUID parentId,
        @Schema(description = "Category name.", example = "Men") @NotBlank @Size(max = 150)
                String name,
        @Schema(description = "Lowercase URL slug.", example = "men")
                @NotBlank
                @Size(max = 180)
                @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
                String slug,
        @Schema(description = "Category description.", example = "Men's clothing.")
                String description,
        @Schema(description = "Display ordering among siblings.", example = "0") @Min(0)
                Integer sortOrder,
        @Schema(description = "Additional JSON metadata.", example = "{\"department\":\"fashion\"}")
                Map<String, Object> metadata) {}

package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(name = "CategoryUpdateRequest", description = "Partial category update fields.")
public record CategoryUpdateRequest(
        @Schema(description = "Category name.", example = "Men's Clothing")
                @Size(min = 1, max = 150)
                String name,
        @Schema(description = "Lowercase URL slug.", example = "mens-clothing")
                @Size(min = 1, max = 180)
                @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
                String slug,
        @Schema(description = "Category description.", example = "Updated description.")
                String description,
        @Schema(description = "Display ordering among siblings.", example = "1") @Min(0)
                Integer sortOrder,
        @Schema(description = "Category JSON metadata.", example = "{\"department\":\"fashion\"}")
                Map<String, Object> metadata) {}

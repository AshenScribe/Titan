package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProductCategoryAssignRequest", description = "Category assignment options.")
public record ProductCategoryAssignRequest(
        @Schema(description = "Whether this assignment is primary.", example = "false")
                boolean primary) {}

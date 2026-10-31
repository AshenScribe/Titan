package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "CategoryParentRequest", description = "New parent assignment for a category.")
public record CategoryParentRequest(
        @Schema(
                        description = "Parent category, or null to make this a root category.",
                        example = "7f8c2c1e-5f8a-4e8d-9f4e-9b6b6a8b7c10")
                UUID parentId) {}

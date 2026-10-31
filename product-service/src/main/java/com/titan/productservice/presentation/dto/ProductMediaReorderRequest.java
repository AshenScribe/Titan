package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Schema(name = "ProductMediaReorderRequest", description = "Complete media ordering for a product.")
public record ProductMediaReorderRequest(
        @Schema(description = "All media IDs exactly once in desired order.") @NotEmpty
                List<@Valid @NotNull UUID> mediaIds) {}

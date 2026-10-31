package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(name = "ErrorResponse", description = "Consistent error envelope returned by the API.")
public record ErrorResponse(
        @Schema(description = "Time the error was generated.", example = "2026-11-01T10:15:30Z")
                OffsetDateTime timestamp,
        @Schema(description = "HTTP status code.", example = "412") int status,
        @Schema(description = "HTTP status reason.", example = "Precondition Failed") String error,
        @Schema(
                        description = "Safe client-facing error message.",
                        example = "Product version is 9, expected 8")
                String message,
        @Schema(description = "Validation or additional details.") List<String> details) {}

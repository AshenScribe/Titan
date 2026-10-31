package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "PageResponse", description = "Bounded paginated response.")
public record PageResponse<T>(
        @Schema(description = "Items on the current page.") List<T> content,
        @Schema(description = "Zero-based current page.", example = "0") int page,
        @Schema(description = "Requested page size.", example = "20") int size,
        @Schema(description = "Total matching items.", example = "42") int totalElements,
        @Schema(description = "Total available pages.", example = "3") int totalPages) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, int totalElements) {
        int totalPages = totalElements == 0 ? 0 : (totalElements + size - 1) / size;
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}

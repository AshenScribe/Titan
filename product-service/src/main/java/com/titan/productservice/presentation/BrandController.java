package com.titan.productservice.presentation;

import com.titan.productservice.business.BrandService;
import com.titan.productservice.presentation.dto.BrandCreateRequest;
import com.titan.productservice.presentation.dto.BrandListQuery;
import com.titan.productservice.presentation.dto.BrandResponse;
import com.titan.productservice.presentation.dto.BrandUpdateRequest;
import com.titan.productservice.presentation.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/brands")
@Tag(name = "Admin Brands", description = "Brand catalog administration")
@ApiResponses({
    @ApiResponse(
            responseCode = "400",
            description = "Malformed or invalid request",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
    @ApiResponse(
            responseCode = "401",
            description = "Authentication is required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
    @ApiResponse(
            responseCode = "500",
            description = "Unexpected server error",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
})
public class BrandController {
    private final BrandService brands;

    public BrandController(BrandService brands) {
        this.brands = brands;
    }

    @PostMapping
    @Operation(summary = "Create a brand")
    @ApiResponse(responseCode = "201", description = "Brand created")
    @ApiResponse(
            responseCode = "409",
            description = "Brand slug already exists",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    public ResponseEntity<BrandResponse> create(@Valid @RequestBody BrandCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BrandResponse.from(brands.create(request)));
    }

    @GetMapping
    @Operation(summary = "List brands")
    public PageResponse<BrandResponse> list(@ParameterObject BrandListQuery query) {
        return brands.list(query);
    }

    @GetMapping("/{brandId}")
    @Operation(summary = "Get a brand")
    @ApiResponse(
            responseCode = "404",
            description = "Brand not found",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    public BrandResponse get(@PathVariable UUID brandId) {
        return BrandResponse.from(brands.get(brandId));
    }

    @PatchMapping("/{brandId}")
    @Operation(summary = "Update a brand")
    public BrandResponse update(
            @PathVariable UUID brandId, @Valid @RequestBody BrandUpdateRequest request) {
        return BrandResponse.from(brands.update(brandId, request));
    }

    @DeleteMapping("/{brandId}")
    @Operation(
            summary = "Delete a brand",
            description = "Deletion is restricted when products reference the brand.")
    @ApiResponse(responseCode = "204", description = "Brand deleted")
    @ApiResponse(
            responseCode = "409",
            description = "Brand is referenced by a product",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    public ResponseEntity<Void> delete(@PathVariable UUID brandId) {
        brands.delete(brandId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{brandId}/status")
    @Operation(summary = "Activate or deactivate a brand")
    public BrandResponse status(@PathVariable UUID brandId, @RequestParam boolean active) {
        return BrandResponse.from(brands.setStatus(brandId, active));
    }
}

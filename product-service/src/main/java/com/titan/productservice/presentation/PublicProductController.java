package com.titan.productservice.presentation;

import com.titan.productservice.business.ProductService;
import com.titan.productservice.presentation.dto.PageResponse;
import com.titan.productservice.presentation.dto.PublicProductListQuery;
import com.titan.productservice.presentation.dto.PublicProductResponse;
import com.titan.productservice.presentation.dto.PublicVariantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Public Products", description = "Published storefront product catalog")
@ApiResponses({
    @ApiResponse(
            responseCode = "400",
            description = "Malformed or invalid request",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
    @ApiResponse(
            responseCode = "404",
            description = "Product or variant not found",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
    @ApiResponse(
            responseCode = "500",
            description = "Unexpected server error",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
})
public class PublicProductController {
    private final ProductService products;

    public PublicProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    @Operation(summary = "List published products")
    public PageResponse<PublicProductResponse> list(@ParameterObject PublicProductListQuery query) {
        return products.publicList(query);
    }

    @GetMapping("/search")
    @Operation(summary = "Search published products")
    public PageResponse<PublicProductResponse> search(
            @ParameterObject PublicProductListQuery query) {
        return products.publicSearch(query);
    }

    @GetMapping("/featured")
    @Operation(
            summary = "List featured products",
            description = "Featured products are marked with metadata.featured=true.")
    public PageResponse<PublicProductResponse> featured(
            @ParameterObject PublicProductListQuery query) {
        return products.featured(query);
    }

    @GetMapping("/new-arrivals")
    @Operation(summary = "List new arrivals", description = "Sorted by publication time.")
    public PageResponse<PublicProductResponse> newArrivals(
            @ParameterObject PublicProductListQuery query) {
        return products.newArrivals(query);
    }

    @GetMapping("/brands/{brandId}")
    @Operation(summary = "List published products for a brand")
    public PageResponse<PublicProductResponse> byBrand(
            @PathVariable UUID brandId, @ParameterObject PublicProductListQuery query) {
        return products.byBrand(brandId, query);
    }

    @GetMapping("/categories/{categoryId}")
    @Operation(summary = "List published products for a category")
    public PageResponse<PublicProductResponse> byCategory(
            @PathVariable UUID categoryId, @ParameterObject PublicProductListQuery query) {
        return products.byCategory(categoryId, query);
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get a published product")
    public PublicProductResponse get(@PathVariable UUID productId) {
        return products.publicGet(productId);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get a published product by slug")
    public PublicProductResponse getBySlug(@PathVariable String slug) {
        return products.publicGetBySlug(slug);
    }

    @GetMapping("/{productId}/variants")
    @Operation(summary = "List active variants")
    public List<PublicVariantResponse> variants(@PathVariable UUID productId) {
        return products.publicVariants(productId);
    }

    @GetMapping("/{productId}/variants/{variantId}")
    @Operation(summary = "Get an active variant")
    public PublicVariantResponse variant(
            @PathVariable UUID productId, @PathVariable UUID variantId) {
        return products.publicVariant(productId, variantId);
    }
}

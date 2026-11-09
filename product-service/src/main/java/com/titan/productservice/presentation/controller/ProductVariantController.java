//------------------------------------------------------------------------------
// MIT License
//
// Copyright (c) 2026 Rishabh Saraswat
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
//------------------------------------------------------------------------------

package com.titan.productservice.presentation.controller;

import com.titan.productservice.presentation.dto.ProductVariantCreateRequest;
import com.titan.productservice.presentation.dto.ProductVariantResponse;
import com.titan.productservice.presentation.dto.ProductVariantUpdateRequest;
import com.titan.productservice.service.ProductService;
import com.titan.productservice.util.IfMatch;
import com.titan.productservice.util.VersionHeaderParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products/{productId}/variants")
@Tag(name = "Admin Product Variants", description = "Product variant administration")
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
            responseCode = "412",
            description = "Product aggregate version mismatch",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
    @ApiResponse(
            responseCode = "500",
            description = "Unexpected server error",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
})
public class ProductVariantController {
    private final ProductService products;

    public ProductVariantController(ProductService products) {
        this.products = products;
    }

    @PostMapping
    @Operation(summary = "Create a product variant")
    @ApiResponse(responseCode = "201", description = "Variant created")
    public ResponseEntity<ProductVariantResponse> create(
            @PathVariable UUID productId,
            @IfMatch @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ProductVariantCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ProductVariantResponse.from(
                                products.createVariant(
                                        productId, request, VersionHeaderParser.parse(ifMatch))));
    }

    @GetMapping
    @Operation(summary = "List product variants")
    public List<ProductVariantResponse> list(@PathVariable UUID productId) {
        return products.listVariants(productId).stream().map(ProductVariantResponse::from).toList();
    }

    @GetMapping("/{variantId}")
    @Operation(summary = "Get a product variant")
    public ProductVariantResponse get(@PathVariable UUID productId, @PathVariable UUID variantId) {
        return ProductVariantResponse.from(products.getVariant(productId, variantId));
    }

    @PatchMapping("/{variantId}")
    @Operation(summary = "Update a product variant")
    public ProductVariantResponse update(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @IfMatch @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ProductVariantUpdateRequest request) {
        return ProductVariantResponse.from(
                products.updateVariant(
                        productId, variantId, request, VersionHeaderParser.parse(ifMatch)));
    }

    @DeleteMapping("/{variantId}")
    @Operation(summary = "Deactivate a product variant")
    @ApiResponse(responseCode = "204", description = "Variant deactivated")
    public ResponseEntity<Void> delete(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @IfMatch @RequestHeader("If-Match") String ifMatch) {
        products.deleteVariant(productId, variantId, VersionHeaderParser.parse(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{variantId}/activate")
    @Operation(summary = "Activate a product variant")
    public ProductVariantResponse activate(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @IfMatch @RequestHeader("If-Match") String ifMatch) {
        return ProductVariantResponse.from(
                products.setVariantStatus(
                        productId, variantId, true, VersionHeaderParser.parse(ifMatch)));
    }

    @PostMapping("/{variantId}/deactivate")
    @Operation(summary = "Deactivate a product variant")
    public ProductVariantResponse deactivate(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @IfMatch @RequestHeader("If-Match") String ifMatch) {
        return ProductVariantResponse.from(
                products.setVariantStatus(
                        productId, variantId, false, VersionHeaderParser.parse(ifMatch)));
    }
}

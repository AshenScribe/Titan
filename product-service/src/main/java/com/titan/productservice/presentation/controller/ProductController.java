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

import com.titan.productservice.presentation.dto.PageResponse;
import com.titan.productservice.presentation.dto.ProductCreateRequest;
import com.titan.productservice.presentation.dto.ProductListQuery;
import com.titan.productservice.presentation.dto.ProductResponse;
import com.titan.productservice.presentation.dto.ProductUpdateRequest;
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
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RequestMapping("/api/v1/admin/products")
@Tag(name = "Admin Products", description = "Product catalog administration")
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
            description = "Product aggregate version does not match If-Match",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
    @ApiResponse(
            responseCode = "500",
            description = "Unexpected server error",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
})
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @Operation(summary = "Create a draft product")
    @ApiResponse(responseCode = "201", description = "Product created")
    @ApiResponse(
            responseCode = "409",
            description = "Slug conflict",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProductResponse.from(productService.create(request)));
    }

    @GetMapping
    @Operation(
            summary = "List products",
            description = "Returns a bounded, paginated product listing.")
    @ApiResponse(responseCode = "200", description = "Product page returned")
    public PageResponse<ProductResponse> list(@ParameterObject ProductListQuery query) {
        return productService.list(query);
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get a product by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product returned"),
        @ApiResponse(
                responseCode = "404",
                description = "Product not found",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    public ProductResponse get(@PathVariable UUID productId) {
        return ProductResponse.from(productService.getIncludingDeleted(productId));
    }

    @PatchMapping("/{productId}")
    @Operation(
            summary = "Update a product",
            description = "Requires the current aggregate version in If-Match.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product updated"),
    })
    public ProductResponse update(
            @PathVariable UUID productId,
            @IfMatch @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ProductUpdateRequest request) {
        return ProductResponse.from(
                productService.update(productId, request, VersionHeaderParser.parse(ifMatch)));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Soft-delete a product")
    public ResponseEntity<Void> delete(
            @PathVariable UUID productId, @IfMatch @RequestHeader("If-Match") String ifMatch) {
        productService.softDelete(productId, VersionHeaderParser.parse(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/publish")
    @Operation(summary = "Publish a product", description = "Requires at least one active variant.")
    public ProductResponse publish(
            @PathVariable UUID productId, @IfMatch @RequestHeader("If-Match") String ifMatch) {
        return transition(productId, ifMatch, "ACTIVE");
    }

    @PostMapping("/{productId}/activate")
    @Operation(
            summary = "Activate a product",
            description = "Requires at least one active variant.")
    public ProductResponse activate(
            @PathVariable UUID productId, @IfMatch @RequestHeader("If-Match") String ifMatch) {
        return transition(productId, ifMatch, "ACTIVE");
    }

    @PostMapping("/{productId}/unpublish")
    @Operation(summary = "Unpublish a product")
    public ProductResponse unpublish(
            @PathVariable UUID productId, @IfMatch @RequestHeader("If-Match") String ifMatch) {
        return transition(productId, ifMatch, "DRAFT");
    }

    @PostMapping("/{productId}/deactivate")
    @Operation(summary = "Deactivate a product")
    public ProductResponse deactivate(
            @PathVariable UUID productId, @IfMatch @RequestHeader("If-Match") String ifMatch) {
        return transition(productId, ifMatch, "INACTIVE");
    }

    private ProductResponse transition(UUID id, String ifMatch, String status) {
        return ProductResponse.from(
                productService.transition(id, status, VersionHeaderParser.parse(ifMatch)));
    }
}

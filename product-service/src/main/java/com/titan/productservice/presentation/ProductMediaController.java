package com.titan.productservice.presentation;

import com.titan.productservice.business.ProductService;
import com.titan.productservice.presentation.dto.ProductMediaCreateRequest;
import com.titan.productservice.presentation.dto.ProductMediaReorderRequest;
import com.titan.productservice.presentation.dto.ProductMediaResponse;
import com.titan.productservice.presentation.dto.ProductMediaUpdateRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products/{productId}/media")
@Tag(name = "Admin Product Media", description = "Product media metadata administration")
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
public class ProductMediaController {
    private final ProductService products;

    public ProductMediaController(ProductService products) {
        this.products = products;
    }

    @PostMapping
    @Operation(summary = "Add product media metadata")
    public ResponseEntity<ProductMediaResponse> create(
            @PathVariable UUID productId,
            @IfMatch @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ProductMediaCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ProductMediaResponse.from(
                                products.createMedia(
                                        productId, request, VersionHeaderParser.parse(ifMatch))));
    }

    @GetMapping
    @Operation(summary = "List product media metadata")
    public List<ProductMediaResponse> list(@PathVariable UUID productId) {
        return products.listMedia(productId).stream().map(ProductMediaResponse::from).toList();
    }

    @PatchMapping("/{mediaId}")
    @Operation(summary = "Update product media metadata")
    public ProductMediaResponse update(
            @PathVariable UUID productId,
            @PathVariable UUID mediaId,
            @IfMatch @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ProductMediaUpdateRequest request) {
        return ProductMediaResponse.from(
                products.updateMedia(
                        productId, mediaId, request, VersionHeaderParser.parse(ifMatch)));
    }

    @DeleteMapping("/{mediaId}")
    @Operation(summary = "Delete product media metadata")
    @ApiResponse(responseCode = "204", description = "Media deleted")
    public ResponseEntity<Void> delete(
            @PathVariable UUID productId,
            @PathVariable UUID mediaId,
            @IfMatch @RequestHeader("If-Match") String ifMatch) {
        products.deleteMedia(productId, mediaId, VersionHeaderParser.parse(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    @Operation(summary = "Reorder product media")
    public List<ProductMediaResponse> reorder(
            @PathVariable UUID productId,
            @IfMatch @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ProductMediaReorderRequest request) {
        return products
                .reorderMedia(productId, request, VersionHeaderParser.parse(ifMatch))
                .stream()
                .map(ProductMediaResponse::from)
                .toList();
    }

    @PutMapping("/{mediaId}/primary")
    @Operation(summary = "Set primary product media")
    public ProductMediaResponse primary(
            @PathVariable UUID productId,
            @PathVariable UUID mediaId,
            @IfMatch @RequestHeader("If-Match") String ifMatch) {
        return ProductMediaResponse.from(
                products.setPrimaryMedia(productId, mediaId, VersionHeaderParser.parse(ifMatch)));
    }
}

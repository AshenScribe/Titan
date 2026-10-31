package com.titan.productservice.presentation;

import com.titan.productservice.business.CategoryService;
import com.titan.productservice.presentation.dto.CategoryCreateRequest;
import com.titan.productservice.presentation.dto.CategoryListQuery;
import com.titan.productservice.presentation.dto.CategoryParentRequest;
import com.titan.productservice.presentation.dto.CategoryResponse;
import com.titan.productservice.presentation.dto.CategoryTreeNode;
import com.titan.productservice.presentation.dto.CategoryUpdateRequest;
import com.titan.productservice.presentation.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin Categories", description = "Category administration and hierarchy management")
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
public class CategoryController {
    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @PostMapping("/api/v1/admin/categories")
    @Operation(summary = "Create a category")
    @ApiResponse(responseCode = "201", description = "Category created")
    @ApiResponse(
            responseCode = "409",
            description = "Slug or hierarchy conflict",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CategoryResponse.from(categories.create(request)));
    }

    @GetMapping("/api/v1/admin/categories")
    @Operation(summary = "List categories")
    public PageResponse<CategoryResponse> list(@ParameterObject CategoryListQuery query) {
        return categories.list(query);
    }

    @GetMapping("/api/v1/admin/categories/{categoryId}")
    @Operation(summary = "Get a category")
    @ApiResponse(
            responseCode = "404",
            description = "Category not found",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    public CategoryResponse get(@PathVariable UUID categoryId) {
        return CategoryResponse.from(categories.get(categoryId));
    }

    @PatchMapping("/api/v1/admin/categories/{categoryId}")
    @Operation(summary = "Update a category")
    public CategoryResponse update(
            @PathVariable UUID categoryId, @Valid @RequestBody CategoryUpdateRequest request) {
        return CategoryResponse.from(categories.update(categoryId, request));
    }

    @DeleteMapping("/api/v1/admin/categories/{categoryId}")
    @Operation(
            summary = "Delete a category",
            description =
                    "Deletion is restricted when children or products reference the category.")
    @ApiResponse(responseCode = "204", description = "Category deleted")
    @ApiResponse(
            responseCode = "409",
            description = "Category is referenced",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    public ResponseEntity<Void> delete(@PathVariable UUID categoryId) {
        categories.delete(categoryId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/v1/admin/categories/{categoryId}/status")
    @Operation(summary = "Activate or deactivate a category")
    public CategoryResponse status(@PathVariable UUID categoryId, @RequestParam boolean active) {
        return CategoryResponse.from(categories.setStatus(categoryId, active));
    }

    @PutMapping("/api/v1/admin/categories/{categoryId}/parent")
    @Operation(
            summary = "Move a category",
            description = "The database trigger prevents hierarchy cycles.")
    public CategoryResponse move(
            @PathVariable UUID categoryId, @Valid @RequestBody CategoryParentRequest request) {
        return CategoryResponse.from(categories.move(categoryId, request));
    }

    @GetMapping("/api/v1/categories/tree")
    @Operation(summary = "Get the category hierarchy")
    public List<CategoryTreeNode> tree() {
        return categories.tree();
    }

    @GetMapping("/api/v1/categories/{categoryId}/children")
    @Operation(summary = "Get direct category children")
    public List<CategoryResponse> children(@PathVariable UUID categoryId) {
        return categories.children(categoryId);
    }

    @GetMapping("/api/v1/categories/{categoryId}/descendants")
    @Operation(summary = "Get all category descendants")
    public List<CategoryResponse> descendants(@PathVariable UUID categoryId) {
        return categories.descendants(categoryId);
    }
}

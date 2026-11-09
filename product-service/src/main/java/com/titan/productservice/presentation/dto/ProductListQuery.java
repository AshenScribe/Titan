// ------------------------------------------------------------------------------
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
// ------------------------------------------------------------------------------

package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ProductListQuery {
    @Schema(description = "Zero-based page number.", example = "0", minimum = "0")
    private int page;

    @Schema(
            description = "Page size, from 1 to 100.",
            example = "20",
            minimum = "1",
            maximum = "100")
    private int size = 20;

    @Schema(description = "Lifecycle status filter.", example = "DRAFT")
    private String status;

    @Schema(
            description = "Brand identifier filter.",
            example = "7f8c2c1e-5f8a-4e8d-9f4e-9b6b6a8b7c10")
    private UUID brandId;

    @Schema(
            description = "Category identifier filter.",
            example = "3f4c2c1e-5f8a-4e8d-9f4e-9b6b6a8b7c11")
    private UUID categoryId;

    @Schema(description = "Whether to include soft-deleted products.", example = "false")
    private Boolean deleted;

    @Schema(
            description = "Sort order.",
            example = "createdAtDesc",
            allowableValues = {
                "createdAtAsc",
                "createdAtDesc",
                "updatedAtAsc",
                "updatedAtDesc",
                "nameAsc",
                "nameDesc"
            })
    private String sort;

    @Schema(
            description = "Inclusive creation timestamp lower bound.",
            example = "2026-01-01T00:00:00Z")
    private OffsetDateTime createdFrom;

    @Schema(
            description = "Inclusive creation timestamp upper bound.",
            example = "2026-12-31T23:59:59Z")
    private OffsetDateTime createdTo;

    @Schema(
            description = "Inclusive update timestamp lower bound.",
            example = "2026-01-01T00:00:00Z")
    private OffsetDateTime updatedFrom;

    @Schema(
            description = "Inclusive update timestamp upper bound.",
            example = "2026-12-31T23:59:59Z")
    private OffsetDateTime updatedTo;

    @Schema(description = "Case-insensitive name fragment.", example = "Oxford")
    private String name;

    @Schema(description = "Case-insensitive slug fragment.", example = "oxford")
    private String slug;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getBrandId() {
        return brandId;
    }

    public void setBrandId(UUID brandId) {
        this.brandId = brandId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public OffsetDateTime getCreatedFrom() {
        return createdFrom;
    }

    public void setCreatedFrom(OffsetDateTime createdFrom) {
        this.createdFrom = createdFrom;
    }

    public OffsetDateTime getCreatedTo() {
        return createdTo;
    }

    public void setCreatedTo(OffsetDateTime createdTo) {
        this.createdTo = createdTo;
    }

    public OffsetDateTime getUpdatedFrom() {
        return updatedFrom;
    }

    public void setUpdatedFrom(OffsetDateTime updatedFrom) {
        this.updatedFrom = updatedFrom;
    }

    public OffsetDateTime getUpdatedTo() {
        return updatedTo;
    }

    public void setUpdatedTo(OffsetDateTime updatedTo) {
        this.updatedTo = updatedTo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }
}

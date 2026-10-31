package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public class CategoryListQuery {
    @Schema(description = "Zero-based page number.", example = "0")
    private int page;

    @Schema(description = "Page size from 1 to 100.", example = "20")
    private int size = 20;

    @Schema(description = "Active status filter.", example = "true")
    private Boolean active;

    @Schema(
            description = "Parent category filter.",
            example = "7f8c2c1e-5f8a-4e8d-9f4e-9b6b6a8b7c10")
    private UUID parentId;

    @Schema(description = "Case-insensitive name fragment.", example = "Men")
    private String name;

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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

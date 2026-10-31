package com.titan.productservice.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class BrandListQuery {
    @Schema(description = "Zero-based page number.", example = "0")
    private int page;

    @Schema(description = "Page size from 1 to 100.", example = "20")
    private int size = 20;

    @Schema(description = "Active status filter.", example = "true")
    private Boolean active;

    @Schema(description = "Case-insensitive name fragment.", example = "Titan")
    private String name;

    @Schema(description = "Case-insensitive slug fragment.", example = "titan")
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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

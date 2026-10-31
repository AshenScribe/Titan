package com.titan.productservice.data.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ProductCategoryEntity {
    private UUID productId;
    private UUID categoryId;
    private boolean primary;
    private OffsetDateTime createdAt;

    public ProductCategoryEntity() {}

    public ProductCategoryEntity(
            UUID productId, UUID categoryId, boolean primary, OffsetDateTime createdAt) {
        this.productId = productId;
        this.categoryId = categoryId;
        this.primary = primary;
        this.createdAt = createdAt;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

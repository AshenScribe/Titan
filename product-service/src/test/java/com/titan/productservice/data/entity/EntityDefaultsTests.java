package com.titan.productservice.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.titan.productservice.data.entity.BrandEntity;
import com.titan.productservice.data.entity.CategoryEntity;
import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.data.entity.ProductVariantEntity;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityDefaultsTests {
    @Test
    void nullMetadataUsesIndependentEmptyMaps() {
        ProductEntity product = new ProductEntity();
        product.setMetadata(null);
        assertNotNull(product.getMetadata());
        assertNotNull(
                new ProductEntity(
                                null, null, null, null, null, null, null, 0, null, null, null, null)
                        .getMetadata());

        BrandEntity brand =
                new BrandEntity(null, null, null, null, null, null, null, true, null, null);
        brand.setMetadata(null);
        assertNotNull(brand.getMetadata());
        assertNotNull(
                new BrandEntity(null, null, null, null, null, null, null, true, null, null)
                        .getMetadata());

        CategoryEntity category =
                new CategoryEntity(null, null, null, null, null, null, 0, true, null, null);
        category.setMetadata(null);
        assertNotNull(category.getMetadata());
        assertNotNull(
                new CategoryEntity(null, null, null, null, null, null, 0, true, null, null)
                        .getMetadata());

        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setAttributes(null);
        assertNotNull(variant.getAttributes());
    }

    @Test
    void metadataMapsAreCopied() {
        var metadata = new HashMap<String, Object>();
        metadata.put("source", "test");
        var product =
                new ProductEntity(
                        UUID.randomUUID(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        metadata,
                        0,
                        null,
                        null,
                        null,
                        null);

        assertNotSame(metadata, product.getMetadata());
        metadata.put("source", "changed");
        assertNotNull(product.getMetadata());
        assertEquals("test", product.getMetadata().get("source"));
    }
}

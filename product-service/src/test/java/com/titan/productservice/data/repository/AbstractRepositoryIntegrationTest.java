package com.titan.productservice.data.repository;

import com.titan.productservice.TestcontainersConfiguration;
import com.titan.productservice.data.entity.BrandEntity;
import com.titan.productservice.data.entity.CategoryEntity;
import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.data.entity.ProductMediaEntity;
import com.titan.productservice.data.entity.ProductVariantEntity;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractRepositoryIntegrationTest {
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected BrandRepository brands;
    @Autowired protected CategoryRepository categories;
    @Autowired protected ProductRepository products;
    @Autowired protected ProductVariantRepository variants;
    @Autowired protected ProductMediaRepository media;
    @Autowired protected ProductCategoryRepository productCategories;

    @AfterEach
    protected void cleanDatabase() {
        jdbc.execute(
                "TRUNCATE product_media, product_variants, product_categories, products, categories, brands CASCADE");
    }

    protected BrandEntity brand(String slug) {
        return new BrandEntity(
                UUID.randomUUID(),
                "Brand " + slug,
                slug,
                null,
                null,
                null,
                Map.of("nested", Map.of("enabled", true)),
                true,
                null,
                null);
    }

    protected CategoryEntity category(String slug, UUID parentId) {
        return new CategoryEntity(
                UUID.randomUUID(),
                parentId,
                "Category " + slug,
                slug,
                null,
                Map.of(),
                0,
                true,
                null,
                null);
    }

    protected ProductEntity product(String slug) {
        return new ProductEntity(
                UUID.randomUUID(),
                null,
                "Product " + slug,
                slug,
                null,
                "DRAFT",
                Map.of("nested", Map.of("value", 1)),
                0,
                null,
                null,
                null,
                null);
    }

    protected ProductVariantEntity variant(UUID productId, String sku, BigDecimal price) {
        ProductVariantEntity value = new ProductVariantEntity();
        value.setId(UUID.randomUUID());
        value.setProductId(productId);
        value.setSku(sku);
        value.setPrice(price);
        value.setAttributes(Map.of("color", "blue"));
        return value;
    }

    protected ProductMediaEntity media(UUID productId, String type, int position) {
        ProductMediaEntity value = new ProductMediaEntity();
        value.setId(UUID.randomUUID());
        value.setProductId(productId);
        value.setMediaType(type);
        value.setStorageKey("media/" + value.getId());
        value.setPosition(position);
        return value;
    }

    protected long productVersion(UUID id) {
        return jdbc.queryForObject("SELECT version FROM products WHERE id = ?", Long.class, id);
    }
}

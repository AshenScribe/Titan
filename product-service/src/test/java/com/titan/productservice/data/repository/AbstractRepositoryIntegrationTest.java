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

package com.titan.productservice.data.repository;

import com.titan.productservice.TestcontainersConfiguration;
import com.titan.productservice.entity.CategoryEntity;
import com.titan.productservice.entity.ProductEntity;
import com.titan.productservice.entity.ProductMediaEntity;
import com.titan.productservice.entity.ProductVariantEntity;
import com.titan.productservice.repository.BrandRepository;
import com.titan.productservice.repository.CategoryRepository;
import com.titan.productservice.repository.ProductCategoryRepository;
import com.titan.productservice.repository.ProductMediaRepository;
import com.titan.productservice.repository.ProductRepository;
import com.titan.productservice.repository.ProductVariantRepository;
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

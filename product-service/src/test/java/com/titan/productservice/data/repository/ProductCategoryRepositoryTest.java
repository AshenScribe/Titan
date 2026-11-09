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

package com.titan.productservice.data.repository;

import com.titan.productservice.TestcontainersConfiguration;
import com.titan.productservice.config.ProductDataSourceConfig;
import com.titan.productservice.data.entity.CategoryEntity;
import com.titan.productservice.data.entity.ProductCategoryEntity;
import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.data.exception.DuplicatePrimaryCategoryException;
import com.titan.productservice.data.exception.ProductConcurrentModificationException;
import com.titan.productservice.data.exception.ProductForeignKeyViolationException;
import com.titan.productservice.data.exception.ProductSQLExceptionTranslator;
import com.titan.productservice.data.exception.ProductUniqueViolationException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, CategoryRepository.class, ProductRepository.class, ProductCategoryRepository.class, ProductDataSourceConfig.class, ProductSQLExceptionTranslator.class})
class ProductCategoryRepositoryTest {

    @Autowired
    private ProductCategoryRepository productCategories;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private ProductRepository products;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM product_variants");
        jdbcTemplate.update("DELETE FROM product_categories");
        jdbcTemplate.update("DELETE FROM product_media");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("UPDATE categories SET parent_id = NULL");
        jdbcTemplate.update("DELETE FROM categories");
    }

    @Test
    void insertPersistsRelationshipAndPopulatesCreatedAt() {
        ProductEntity product = product("categories");
        products.insert(product);
        CategoryEntity first = category("first", null);
        categories.insert(first);

        productCategories.insert(new ProductCategoryEntity(product.getId(), first.getId(), true, null), 0);

        List<ProductCategoryEntity> byProduct = productCategories.findByProductId(product.getId());
        Assertions.assertThat(byProduct).hasSize(1);
        Assertions.assertThat(byProduct.getFirst().getProductId()).isEqualTo(product.getId());
        Assertions.assertThat(byProduct.getFirst().getCategoryId()).isEqualTo(first.getId());
        Assertions.assertThat(byProduct.getFirst().isPrimary()).isTrue();
        Assertions.assertThat(byProduct.getFirst().getCreatedAt()).isNotNull();

        Assertions.assertThat(productCategories.findByCategoryId(first.getId())).hasSize(1);
    }

    @Test
    void findByProductIdOrdersPrimaryFirstThenByCategoryId() {
        ProductEntity product = product("ordered");
        products.insert(product);
        CategoryEntity lower = category("lower", null);
        CategoryEntity higher = category("higher", null);
        categories.insert(lower);
        categories.insert(higher);

        productCategories.insert(new ProductCategoryEntity(product.getId(), lower.getId(), false, null), 0);
        productCategories.insert(new ProductCategoryEntity(product.getId(), higher.getId(), false, null), 1);
        productCategories.update(new ProductCategoryEntity(product.getId(), higher.getId(), true, null), 2);

        Assertions.assertThat(productCategories.findByProductId(product.getId())).extracting(ProductCategoryEntity::getCategoryId).containsExactly(higher.getId(), lower.getId());
    }

    @Test
    void findByProductIdReturnsEmptyForUnknownProduct() {
        Assertions.assertThat(productCategories.findByProductId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByCategoryIdReturnsEmptyForUnknownCategory() {
        Assertions.assertThat(productCategories.findByCategoryId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void updateMutatesPrimaryFlag() {
        ProductEntity product = product("mutate");
        products.insert(product);
        CategoryEntity value = category("mutate", null);
        categories.insert(value);

        productCategories.insert(new ProductCategoryEntity(product.getId(), value.getId(), true, null), 0);

        Assertions.assertThat(productCategories.update(new ProductCategoryEntity(product.getId(), value.getId(), false, null), 1)).isEqualTo(1);

        Assertions.assertThat(productCategories.findByProductId(product.getId()).getFirst().isPrimary()).isFalse();
    }

    @Test
    void updateOnMissingRelationshipIsRejected() {
        ProductEntity product = product("missing-rel");
        products.insert(product);
        CategoryEntity value = category("missing-rel", null);
        categories.insert(value);

        Assertions.assertThatThrownBy(() -> productCategories.update(new ProductCategoryEntity(product.getId(), value.getId(), true, null), 0)).isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void deleteRemovesRelationshipAndReturnsAffectedCount() {
        ProductEntity product = product("delete-rel");
        products.insert(product);
        CategoryEntity value = category("delete-rel", null);
        categories.insert(value);

        productCategories.insert(new ProductCategoryEntity(product.getId(), value.getId(), false, null), 0);

        Assertions.assertThat(productCategories.delete(product.getId(), value.getId(), 1)).isEqualTo(1);
        Assertions.assertThat(productCategories.findByProductId(product.getId())).isEmpty();

        Assertions.assertThatThrownBy(() -> productCategories.delete(product.getId(), value.getId(), 2)).isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void setPrimaryClearsPreviousPrimaryAndSetsNew() {
        ProductEntity product = product("switch");
        products.insert(product);
        CategoryEntity first = category("switch-first", null);
        CategoryEntity second = category("switch-second", null);
        categories.insert(first);
        categories.insert(second);

        productCategories.insert(new ProductCategoryEntity(product.getId(), first.getId(), true, null), 0);
        productCategories.insert(new ProductCategoryEntity(product.getId(), second.getId(), false, null), 1);

        Assertions.assertThat(productCategories.setPrimary(product.getId(), second.getId(), 2)).isEqualTo(1);

        List<ProductCategoryEntity> relations = productCategories.findByProductId(product.getId());
        Assertions.assertThat(relations).hasSize(2);
        Assertions.assertThat(relations.getFirst().getCategoryId()).isEqualTo(second.getId());
        Assertions.assertThat(relations.getFirst().isPrimary()).isTrue();
        Assertions.assertThat(relations.stream().filter(r -> r.getCategoryId().equals(first.getId())).findFirst().orElseThrow().isPrimary()).isFalse();
    }

    @Test
    void setPrimaryOnMissingRelationshipIsRejected() {
        ProductEntity product = product("set-missing");
        products.insert(product);
        CategoryEntity value = category("set-missing", null);
        categories.insert(value);

        Assertions.assertThatThrownBy(() -> productCategories.setPrimary(product.getId(), value.getId(), 0)).isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void insertReplacingPrimaryReplacesExistingPrimary() {
        ProductEntity product = product("replace-primary");
        products.insert(product);
        CategoryEntity first = category("replace-first", null);
        CategoryEntity second = category("replace-second", null);
        categories.insert(first);
        categories.insert(second);

        productCategories.insertReplacingPrimary(new ProductCategoryEntity(product.getId(), first.getId(), true, null), 0);
        productCategories.insertReplacingPrimary(new ProductCategoryEntity(product.getId(), second.getId(), true, null), 1);

        List<ProductCategoryEntity> relations = productCategories.findByProductId(product.getId());
        Assertions.assertThat(relations).hasSize(2);
        Assertions.assertThat(relations.getFirst().getCategoryId()).isEqualTo(second.getId());
        Assertions.assertThat(relations.getFirst().isPrimary()).isTrue();
    }

    @Test
    void replaceClearsAndReinsertsAllRelationships() {
        ProductEntity product = product("replace-all");
        products.insert(product);
        CategoryEntity first = category("replace-a", null);
        CategoryEntity second = category("replace-b", null);
        CategoryEntity third = category("replace-c", null);
        categories.insert(first);
        categories.insert(second);
        categories.insert(third);

        productCategories.insert(
                new ProductCategoryEntity(product.getId(), first.getId(), true, null), 0);

        productCategories.replace(product.getId(), List.of(new ProductCategoryEntity(product.getId(), second.getId(), true, null), new ProductCategoryEntity(product.getId(), third.getId(), false, null)), 1);

        Assertions.assertThat(productCategories.findByProductId(product.getId())).extracting(ProductCategoryEntity::getCategoryId).containsExactlyInAnyOrder(second.getId(), third.getId());
    }

    @Test
    void replaceWithEmptyListClearsAllRelationships() {
        ProductEntity product = product("replace-empty");
        products.insert(product);
        CategoryEntity value = category("replace-empty", null);
        categories.insert(value);

        productCategories.insert(new ProductCategoryEntity(product.getId(), value.getId(), false, null), 0);

        productCategories.replace(product.getId(), List.of(), 1);

        Assertions.assertThat(productCategories.findByProductId(product.getId())).isEmpty();
    }

    @Test
    void insertBatchHandlesNullEmptyAndMultiple() {
        ProductEntity product = product("batch");
        products.insert(product);
        CategoryEntity first = category("batch-a", null);
        CategoryEntity second = category("batch-b", null);
        categories.insert(first);
        categories.insert(second);

        Assertions.assertThat(productCategories.insertBatch(null)).isEmpty();
        Assertions.assertThat(productCategories.insertBatch(List.of())).isEmpty();

        Assertions.assertThat(productCategories.insertBatch(List.of(new ProductCategoryEntity(product.getId(), first.getId(), true, null), new ProductCategoryEntity(product.getId(), second.getId(), false, null)))).hasSize(2);

        Assertions.assertThat(productCategories.findByProductId(product.getId())).hasSize(2);
    }

    @Test
    void insertBatchRejectsMultipleDistinctProducts() {
        ProductEntity p1 = product("batch-p1");
        ProductEntity p2 = product("batch-p2");
        products.insert(p1);
        products.insert(p2);
        CategoryEntity value = category("batch-multi", null);
        categories.insert(value);

        Assertions.assertThatThrownBy(() -> productCategories.insertBatch(List.of(new ProductCategoryEntity(p1.getId(), value.getId(), false, null), new ProductCategoryEntity(p2.getId(), value.getId(), false, null)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staleProductVersionIsRejectedOnInsert() {
        ProductEntity product = product("stale-insert");
        products.insert(product);
        CategoryEntity value = category("stale-insert", null);
        categories.insert(value);

        Assertions.assertThatThrownBy(() -> productCategories.insert(new ProductCategoryEntity(product.getId(), value.getId(), false, null), 99)).isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void staleProductVersionIsRejectedOnUpdate() {
        ProductEntity product = product("stale-update");
        products.insert(product);
        CategoryEntity value = category("stale-update", null);
        categories.insert(value);

        productCategories.insert(new ProductCategoryEntity(product.getId(), value.getId(), false, null), 0);

        Assertions.assertThatThrownBy(() -> productCategories.update(new ProductCategoryEntity(product.getId(), value.getId(), true, null), 99)).isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void staleProductVersionIsRejectedOnDelete() {
        ProductEntity product = product("stale-delete");
        products.insert(product);
        CategoryEntity value = category("stale-delete", null);
        categories.insert(value);

        productCategories.insert(new ProductCategoryEntity(product.getId(), value.getId(), false, null), 0);

        Assertions.assertThatThrownBy(() -> productCategories.delete(product.getId(), value.getId(), 99)).isInstanceOf(ProductConcurrentModificationException.class);
    }

    @ParameterizedTest(name = "staleVersion={0}")
    @MethodSource("staleVersions")
    void everyMutationRejectsStaleVersion(long staleVersion) {
        ProductEntity product = product("stale-" + staleVersion);
        products.insert(product);
        CategoryEntity value = category("stale-" + staleVersion, null);
        categories.insert(value);

        productCategories.insert(new ProductCategoryEntity(product.getId(), value.getId(), false, null), 0);

        Assertions.assertThatThrownBy(() -> productCategories.update(new ProductCategoryEntity(product.getId(), value.getId(), true, null), staleVersion)).isInstanceOf(ProductConcurrentModificationException.class);
    }

    static Stream<Arguments> staleVersions() {
        return Stream.of(Arguments.of(0L), Arguments.of(5L), Arguments.of(99L));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void duplicatePrimaryCategoryIsRejected() {
        ProductEntity product = product("dup-primary");
        products.insert(product);
        CategoryEntity first = category("dup-primary-a", null);
        CategoryEntity second = category("dup-primary-b", null);
        categories.insert(first);
        categories.insert(second);

        productCategories.insert(new ProductCategoryEntity(product.getId(), first.getId(), true, null), 0);

        Assertions.assertThatThrownBy(() -> productCategories.insert(new ProductCategoryEntity(product.getId(), second.getId(), true, null), 1)).isInstanceOf(DuplicatePrimaryCategoryException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void duplicateRelationshipIsRejected() {
        ProductEntity product = product("dup-rel");
        products.insert(product);
        CategoryEntity value = category("dup-rel", null);
        categories.insert(value);

        productCategories.insert(new ProductCategoryEntity(product.getId(), value.getId(), false, null), 0);

        Assertions.assertThatThrownBy(() -> productCategories.insert(new ProductCategoryEntity(product.getId(), value.getId(), false, null), 1)).isInstanceOf(ProductUniqueViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void insertingWithUnknownCategoryIsRejected() {
        ProductEntity product = product("unknown-category");
        products.insert(product);

        Assertions.assertThatThrownBy(() -> productCategories.insert(new ProductCategoryEntity(product.getId(), UUID.randomUUID(), false, null), 0)).isInstanceOf(ProductForeignKeyViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void insertingWithUnknownProductIsRejected() {
        CategoryEntity value = category("unknown-product", null);
        categories.insert(value);

        Assertions.assertThatThrownBy(() -> productCategories.insert(new ProductCategoryEntity(UUID.randomUUID(), value.getId(), false, null), 0)).isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void batchFailureDoesNotCommitAnyRow() {
        ProductEntity product = product("batch-fail");
        products.insert(product);
        CategoryEntity value = category("batch-fail", null);
        categories.insert(value);

        ProductCategoryEntity relation = new ProductCategoryEntity(product.getId(), value.getId(), false, null);

        Assertions.assertThatThrownBy(() -> productCategories.insertBatch(List.of(relation, relation))).isInstanceOf(ProductUniqueViolationException.class);

        Assertions.assertThat(productCategories.findByProductId(product.getId())).isEmpty();
    }

    private CategoryEntity category(String slug, UUID parentId) {
        return new CategoryEntity(UUID.randomUUID(), parentId, "Category " + slug, slug, null, Map.of("nested", Map.of("enabled", true)), 0, true, null, null);
    }

    private ProductEntity product(String slug) {
        return new ProductEntity(UUID.randomUUID(), null, "Product " + slug, slug, null, "DRAFT", Map.of("nested", Map.of("value", 1)), 0, null, null, null, null);
    }
}

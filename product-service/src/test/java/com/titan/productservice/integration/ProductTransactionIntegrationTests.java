package com.titan.productservice.integration;

import com.titan.productservice.TestcontainersConfiguration;
import com.titan.productservice.config.ProductDataSourceConfig;
import com.titan.productservice.data.entity.*;
import com.titan.productservice.data.exception.*;
import com.titan.productservice.data.repository.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    TestcontainersConfiguration.class,
    BrandRepository.class,
    CategoryRepository.class,
    ProductRepository.class,
    ProductVariantRepository.class,
    ProductMediaRepository.class,
    ProductCategoryRepository.class,
    ProductDataSourceConfig.class,
    ProductSQLExceptionTranslator.class
})
class ProductTransactionIntegrationTests {

    @Autowired private TransactionTemplate transactions;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private ProductMediaRepository media;
    @Autowired private ProductCategoryRepository productCategories;
    @Autowired private CategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.execute(
                "TRUNCATE TABLE product_variants, product_categories, product_media, products, categories, brands CASCADE");
    }

    @Test
    void failedVariantInsertRollsBackParentVersionAndEarlierInsert() {
        ProductEntity product = product("rollback-variant");
        products.insert(product);
        Assertions.assertThatThrownBy(
                        () ->
                                transactions.executeWithoutResult(
                                        status -> {
                                            variants.insert(
                                                    variant(
                                                            product.getId(),
                                                            "valid",
                                                            BigDecimal.ONE),
                                                    0);
                                            variants.insert(
                                                    variant(
                                                            product.getId(),
                                                            "invalid",
                                                            BigDecimal.valueOf(-1)),
                                                    1);
                                        }))
                .isInstanceOf(InvalidVariantPriceException.class);
        Assertions.assertThat(productVersion(product.getId())).isZero();
        Assertions.assertThat(variantCount(product.getId())).isZero();
    }

    @Test
    void failedMediaMutationRollsBackParentAndMediaRows() {
        ProductEntity product = product("rollback-media");
        products.insert(product);
        Assertions.assertThatThrownBy(
                        () ->
                                transactions.executeWithoutResult(
                                        status -> {
                                            media.insert(media(product.getId(), "IMAGE", 0), 0);
                                            media.insert(media(product.getId(), "AUDIO", 1), 1);
                                        }))
                .isInstanceOf(ProductCheckConstraintViolationException.class);
        Assertions.assertThat(productVersion(product.getId())).isZero();
        Assertions.assertThat(mediaCount(product.getId())).isZero();
    }

    @Test
    void failedBatchDoesNotLeavePartialRows() {
        ProductEntity product = product("rollback-batch");
        products.insert(product);
        Assertions.assertThatThrownBy(
                        () ->
                                transactions.executeWithoutResult(
                                        status ->
                                                variants.insertBatch(
                                                        List.of(
                                                                variant(
                                                                        product.getId(),
                                                                        "batch-valid",
                                                                        BigDecimal.ONE),
                                                                variant(
                                                                        product.getId(),
                                                                        "batch-invalid",
                                                                        BigDecimal.valueOf(-1))))))
                .isInstanceOf(InvalidVariantPriceException.class);
        Assertions.assertThat(variantCount(product.getId())).isZero();
        Assertions.assertThat(productVersion(product.getId())).isZero();
    }

    @Test
    void failedMediaBatchDoesNotLeavePartialRowsOrVersionChanges() {
        ProductEntity product = product("rollback-media-batch");
        products.insert(product);
        Assertions.assertThatThrownBy(
                        () ->
                                transactions.executeWithoutResult(
                                        status ->
                                                media.insertBatch(
                                                        List.of(
                                                                media(product.getId(), "IMAGE", 0),
                                                                media(
                                                                        product.getId(),
                                                                        "AUDIO",
                                                                        1)))))
                .isInstanceOf(ProductCheckConstraintViolationException.class);
        Assertions.assertThat(mediaCount(product.getId())).isZero();
        Assertions.assertThat(productVersion(product.getId())).isZero();
    }

    @Test
    void failedProductCategoryBatchDoesNotLeavePartialRowsOrVersionChanges() {
        ProductEntity product = product("rollback-category-batch");
        products.insert(product);
        CategoryEntity category = category("rollback-category", null);
        categories.insert(category);
        Assertions.assertThatThrownBy(
                        () ->
                                transactions.executeWithoutResult(
                                        status ->
                                                productCategories.insertBatch(
                                                        List.of(
                                                                new ProductCategoryEntity(
                                                                        product.getId(),
                                                                        category.getId(),
                                                                        false,
                                                                        null),
                                                                new ProductCategoryEntity(
                                                                        product.getId(),
                                                                        UUID.randomUUID(),
                                                                        false,
                                                                        null)))))
                .isInstanceOf(ProductForeignKeyViolationException.class);
        Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM product_categories WHERE product_id = ?",
                                Integer.class,
                                product.getId()))
                .isZero();
        Assertions.assertThat(productVersion(product.getId())).isZero();
    }

    @Test
    void deletedProductRejectsChildMutations() {
        ProductEntity product = product("deleted-parent");
        products.insert(product);
        Assertions.assertThat(products.softDeleteById(product.getId(), 0)).isPresent();

        Assertions.assertThatThrownBy(
                        () ->
                                variants.insert(
                                        variant(product.getId(), "deleted-variant", BigDecimal.ONE),
                                        1))
                .isInstanceOf(ProductConcurrentModificationException.class);
        Assertions.assertThatThrownBy(() -> media.insert(media(product.getId(), "IMAGE", 0), 1))
                .isInstanceOf(ProductConcurrentModificationException.class);
        CategoryEntity category = category("deleted-parent-category", null);
        categories.insert(category);
        Assertions.assertThatThrownBy(
                        () ->
                                productCategories.insert(
                                        new ProductCategoryEntity(
                                                product.getId(), category.getId(), false, null),
                                        1))
                .isInstanceOf(ProductConcurrentModificationException.class);

        Assertions.assertThat(variantCount(product.getId())).isZero();
        Assertions.assertThat(mediaCount(product.getId())).isZero();
        Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM product_categories WHERE product_id = ?",
                                Integer.class,
                                product.getId()))
                .isZero();
    }

    @Test
    void deferredMediaPositionConstraintFailsAtCommitAndRollsBack() {
        ProductEntity product = product("deferred-position");
        products.insert(product);
        Assertions.assertThatThrownBy(
                        () ->
                                transactions.executeWithoutResult(
                                        status -> {
                                            media.insert(media(product.getId(), "IMAGE", 0), 0);
                                            media.insert(media(product.getId(), "VIDEO", 1), 1);
                                            jdbc.update(
                                                    "UPDATE product_media SET position = 1 WHERE product_id = ?",
                                                    product.getId());
                                        }))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        Assertions.assertThat(mediaCount(product.getId())).isZero();
    }

    @Test
    void productUpdateIncrementsVersionAndUpdatedTimestamp() {
        ProductEntity product = product("timestamp-version");
        products.insert(product);
        ProductEntity before = products.findById(product.getId()).orElseThrow();
        product.setName("timestamp-version-updated");
        products.update(product);
        ProductEntity after = products.findById(product.getId()).orElseThrow();
        Assertions.assertThat(after.getVersion()).isEqualTo(before.getVersion() + 1);
        Assertions.assertThat(after.getUpdatedAt()).isNotNull();
        Assertions.assertThat(after.getUpdatedAt()).isAfterOrEqualTo(before.getUpdatedAt());
    }

    private long productVersion(UUID id) {
        return jdbc.queryForObject("SELECT version FROM products WHERE id = ?", Long.class, id);
    }

    private long variantCount(UUID productId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM product_variants WHERE product_id = ?",
                Long.class,
                productId);
    }

    private long mediaCount(UUID productId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM product_media WHERE product_id = ?", Long.class, productId);
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
}

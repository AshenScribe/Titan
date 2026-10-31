package com.titan.productservice.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.titan.productservice.data.repository.AbstractRepositoryIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class ProductTransactionIntegrationTests extends AbstractRepositoryIntegrationTest {
    @Autowired private TransactionTemplate transactions;

    @Test
    void failedVariantInsertRollsBackParentVersionAndEarlierInsert() {
        var product = product("rollback-variant");
        products.insert(product);
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        transactions.executeWithoutResult(
                                status -> {
                                    var valid = variant(product.getId(), "valid", BigDecimal.ONE);
                                    variants.insert(valid, 0);
                                    var invalid =
                                            variant(
                                                    product.getId(),
                                                    "invalid",
                                                    BigDecimal.valueOf(-1));
                                    variants.insert(invalid, 1);
                                }));
        assertEquals(0, productVersion(product.getId()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_variants WHERE product_id = ?",
                        Integer.class,
                        product.getId()));
    }

    @Test
    void failedMediaMutationRollsBackParentAndMediaRows() {
        var product = product("rollback-media");
        products.insert(product);
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        transactions.executeWithoutResult(
                                status -> {
                                    media.insert(media(product.getId(), "IMAGE", 0), 0);
                                    media.insert(media(product.getId(), "AUDIO", 1), 1);
                                }));
        assertEquals(0, productVersion(product.getId()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_media WHERE product_id = ?",
                        Integer.class,
                        product.getId()));
    }

    @Test
    void failedBatchDoesNotLeavePartialRows() {
        var product = product("rollback-batch");
        products.insert(product);
        var valid = variant(product.getId(), "batch-valid", BigDecimal.ONE);
        var invalid = variant(product.getId(), "batch-invalid", BigDecimal.valueOf(-1));
        assertThrows(
                DataAccessException.class,
                () ->
                        transactions.executeWithoutResult(
                                status -> variants.insertBatch(List.of(valid, invalid))));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_variants WHERE product_id = ?",
                        Integer.class,
                        product.getId()));
        assertEquals(0, productVersion(product.getId()));
    }

    @Test
    void failedMediaBatchDoesNotLeavePartialRowsOrVersionChanges() {
        var product = product("rollback-media-batch");
        products.insert(product);
        var valid = media(product.getId(), "IMAGE", 0);
        var invalid = media(product.getId(), "AUDIO", 1);
        assertThrows(
                DataAccessException.class,
                () ->
                        transactions.executeWithoutResult(
                                status -> media.insertBatch(List.of(valid, invalid))));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_media WHERE product_id = ?",
                        Integer.class,
                        product.getId()));
        assertEquals(0, productVersion(product.getId()));
    }

    @Test
    void failedProductCategoryBatchDoesNotLeavePartialRowsOrVersionChanges() {
        var product = product("rollback-category-batch");
        products.insert(product);
        var category = category("rollback-category", null);
        categories.insert(category);
        var valid =
                new com.titan.productservice.data.entity.ProductCategoryEntity(
                        product.getId(), category.getId(), false, null);
        var invalid =
                new com.titan.productservice.data.entity.ProductCategoryEntity(
                        product.getId(), java.util.UUID.randomUUID(), false, null);
        assertThrows(
                DataAccessException.class,
                () ->
                        transactions.executeWithoutResult(
                                status -> productCategories.insertBatch(List.of(valid, invalid))));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_categories WHERE product_id = ?",
                        Integer.class,
                        product.getId()));
        assertEquals(0, productVersion(product.getId()));
    }

    @Test
    void deletedProductRejectsChildMutations() {
        var product = product("deleted-parent");
        products.insert(product);
        assertTrue(products.softDeleteById(product.getId(), 0).isPresent());

        assertThrows(
                DataAccessException.class,
                () ->
                        variants.insert(
                                variant(product.getId(), "deleted-variant", BigDecimal.ONE), 1));
        assertThrows(
                DataAccessException.class,
                () -> media.insert(media(product.getId(), "IMAGE", 0), 1));
        var category = category("deleted-parent-category", null);
        categories.insert(category);
        assertThrows(
                DataAccessException.class,
                () ->
                        productCategories.insert(
                                new com.titan.productservice.data.entity.ProductCategoryEntity(
                                        product.getId(), category.getId(), false, null),
                                1));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_variants WHERE product_id = ?",
                        Integer.class,
                        product.getId()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_media WHERE product_id = ?",
                        Integer.class,
                        product.getId()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_categories WHERE product_id = ?",
                        Integer.class,
                        product.getId()));
    }

    @Test
    void deferredMediaPositionConstraintFailsAtCommitAndRollsBack() {
        var product = product("deferred-position");
        products.insert(product);
        assertThrows(
                DataAccessException.class,
                () ->
                        transactions.executeWithoutResult(
                                status -> {
                                    media.insert(media(product.getId(), "IMAGE", 0), 0);
                                    media.insert(media(product.getId(), "VIDEO", 1), 1);
                                    jdbc.update(
                                            "UPDATE product_media SET position = 1 WHERE product_id = ?",
                                            product.getId());
                                }));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_media WHERE product_id = ?",
                        Integer.class,
                        product.getId()));
    }

    @Test
    void productUpdateIncrementsVersionAndUpdatedTimestamp() {
        var product = product("timestamp-version");
        products.insert(product);
        var before = products.findById(product.getId()).orElseThrow();
        product.setName("timestamp-version-updated");
        products.update(product);
        var after = products.findById(product.getId()).orElseThrow();
        assertEquals(before.getVersion() + 1, after.getVersion());
        assertNotNull(after.getUpdatedAt());
        assertFalse(after.getUpdatedAt().isBefore(before.getUpdatedAt()));
    }
}

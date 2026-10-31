package com.titan.productservice.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.data.repository.AbstractRepositoryIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

class ProductConcurrencyIntegrationTests extends AbstractRepositoryIntegrationTest {
    @Test
    void concurrentProductUpdatesHaveOneOptimisticWinner() throws Exception {
        var product = product("concurrent-product");
        products.insert(product);
        var barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () -> updateAfterBarrier(copy(product, "first"), barrier),
                        () -> updateAfterBarrier(copy(product, "second"), barrier));
        assertExactlyOneSuccessAndOneOptimisticConflict(results);
        assertEquals(1, productVersion(product.getId()));
    }

    @Test
    void concurrentVariantInsertionsUseParentExpectedVersion() throws Exception {
        var product = product("concurrent-variants");
        products.insert(product);
        var barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () -> insertVariantAfterBarrier(product.getId(), "a", barrier, 0),
                        () -> insertVariantAfterBarrier(product.getId(), "b", barrier, 0));
        assertExactlyOneSuccessAndOneOptimisticConflict(results);
        assertEquals(1, productVersion(product.getId()));
    }

    @Test
    void concurrentDuplicateSkuAcrossDifferentProductsHasOneUniqueWinner() throws Exception {
        var firstProduct = product("sku-product-a");
        var secondProduct = product("sku-product-b");
        products.insert(firstProduct);
        products.insert(secondProduct);
        var barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () ->
                                insertVariantAfterBarrier(
                                        firstProduct.getId(), "same-sku", barrier, 0),
                        () ->
                                insertVariantAfterBarrier(
                                        secondProduct.getId(), "same-sku", barrier, 0));
        assertEquals(1, results.stream().filter(Success.class::isInstance).count());
        assertEquals(1, results.stream().filter(IntegrityConflict.class::isInstance).count());
        assertTrue(results.stream().noneMatch(OptimisticConflict.class::isInstance));
        assertTrue(results.stream().noneMatch(UnexpectedFailure.class::isInstance));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_variants WHERE LOWER(sku) = LOWER(?)",
                        Integer.class,
                        "same-sku"));
    }

    @Test
    void concurrentPrimaryMediaAssignmentsHaveOneAggregateWinner() throws Exception {
        var product = product("primary-media-race");
        products.insert(product);
        var first = media(product.getId(), "IMAGE", 0);
        var second = media(product.getId(), "IMAGE", 1);
        var barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () -> insertMediaAfterBarrier(first, barrier, 0),
                        () -> insertMediaAfterBarrier(second, barrier, 0));
        assertExactlyOneSuccessAndOneOptimisticConflict(results);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_media WHERE product_id = ? AND is_primary = TRUE",
                        Integer.class,
                        product.getId()));
    }

    @Test
    void concurrentPrimaryCategoryAssignmentsHaveOneAggregateWinner() throws Exception {
        var product = product("primary-category-race");
        products.insert(product);
        var first = category("race-category-a", null);
        var second = category("race-category-b", null);
        categories.insert(first);
        categories.insert(second);
        var barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () ->
                                insertCategoryAfterBarrier(
                                        product.getId(), first.getId(), barrier, 0),
                        () ->
                                insertCategoryAfterBarrier(
                                        product.getId(), second.getId(), barrier, 0));
        assertExactlyOneSuccessAndOneOptimisticConflict(results);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_categories WHERE product_id = ? AND is_primary = TRUE",
                        Integer.class,
                        product.getId()));
    }

    @Test
    void concurrentCategoryMovesCannotCreateACycle() throws Exception {
        var firstRoot = category("cycle-root-a", null);
        var secondRoot = category("cycle-root-b", null);
        categories.insert(firstRoot);
        categories.insert(secondRoot);
        var barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () -> moveCategoryAfterBarrier(firstRoot, secondRoot.getId(), barrier),
                        () -> moveCategoryAfterBarrier(secondRoot, firstRoot.getId(), barrier));
        assertEquals(1, results.stream().filter(Success.class::isInstance).count());
        assertEquals(1, results.stream().filter(IntegrityConflict.class::isInstance).count());
        assertTrue(results.stream().noneMatch(UnexpectedFailure.class::isInstance));
        assertNoCategoryCycle();
    }

    @Test
    void concurrentDeactivationOfFinalActiveVariantsLeavesOneActive() throws Exception {
        var product = product("final-variant-race");
        products.insert(product);
        var first = variant(product.getId(), "final-a", BigDecimal.ONE);
        var second = variant(product.getId(), "final-b", BigDecimal.valueOf(2));
        variants.insert(first, 0);
        variants.insert(second, 1);
        product.setStatus("ACTIVE");
        product.setPublishedAt(java.time.OffsetDateTime.now());
        product.setVersion(2);
        products.update(product);
        long expectedProductVersion = productVersion(product.getId());
        var barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () ->
                                deactivateAfterBarrier(
                                        new DeactivationAttempt(
                                                first.getId(),
                                                product.getId(),
                                                0,
                                                expectedProductVersion),
                                        barrier),
                        () ->
                                deactivateAfterBarrier(
                                        new DeactivationAttempt(
                                                second.getId(),
                                                product.getId(),
                                                0,
                                                expectedProductVersion),
                                        barrier));
        assertExactlyOneSuccessAndOneOptimisticConflict(results);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_variants WHERE product_id = ? AND status = 'ACTIVE'",
                        Integer.class,
                        product.getId()));
        assertEquals("ACTIVE", products.findById(product.getId()).orElseThrow().getStatus());
    }

    private List<ConcurrencyResult> run(
            Callable<ConcurrencyResult> first, Callable<ConcurrencyResult> second)
            throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<ConcurrencyResult>> futures =
                    pool.invokeAll(List.of(first, second), 10, TimeUnit.SECONDS);
            List<ConcurrencyResult> results = futures.stream().map(this::resultOf).toList();
            assertEquals(2, results.size());
            return results;
        }
    }

    private ConcurrencyResult resultOf(Future<ConcurrencyResult> future) {
        try {
            assertFalse(future.isCancelled(), "concurrency task timed out");
            return future.get(1, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Unexpected concurrency task failure", exception);
        }
    }

    private ConcurrencyResult updateAfterBarrier(ProductEntity value, CyclicBarrier barrier) {
        await(barrier);
        try {
            products.update(value);
            return new Success();
        } catch (OptimisticLockingFailureException exception) {
            return new OptimisticConflict(exception);
        } catch (RuntimeException exception) {
            return new UnexpectedFailure(exception);
        }
    }

    private ConcurrencyResult insertVariantAfterBarrier(
            UUID productId, String sku, CyclicBarrier barrier, long expectedVersion) {
        await(barrier);
        try {
            variants.insert(variant(productId, sku, BigDecimal.ONE), expectedVersion);
            return new Success();
        } catch (DataIntegrityViolationException exception) {
            return new IntegrityConflict(exception);
        } catch (OptimisticLockingFailureException exception) {
            return new OptimisticConflict(exception);
        } catch (RuntimeException exception) {
            return new UnexpectedFailure(exception);
        }
    }

    private ConcurrencyResult insertMediaAfterBarrier(
            com.titan.productservice.data.entity.ProductMediaEntity value,
            CyclicBarrier barrier,
            long expectedVersion) {
        value.setPrimary(true);
        await(barrier);
        try {
            media.insert(value, expectedVersion);
            return new Success();
        } catch (OptimisticLockingFailureException exception) {
            return new OptimisticConflict(exception);
        } catch (RuntimeException exception) {
            return new UnexpectedFailure(exception);
        }
    }

    private ConcurrencyResult insertCategoryAfterBarrier(
            UUID productId, UUID categoryId, CyclicBarrier barrier, long expectedVersion) {
        await(barrier);
        try {
            productCategories.insert(
                    new com.titan.productservice.data.entity.ProductCategoryEntity(
                            productId, categoryId, true, null),
                    expectedVersion);
            return new Success();
        } catch (OptimisticLockingFailureException exception) {
            return new OptimisticConflict(exception);
        } catch (RuntimeException exception) {
            return new UnexpectedFailure(exception);
        }
    }

    private ConcurrencyResult moveCategoryAfterBarrier(
            com.titan.productservice.data.entity.CategoryEntity value,
            UUID parentId,
            CyclicBarrier barrier) {
        value.setParentId(parentId);
        await(barrier);
        try {
            categories.update(value);
            return new Success();
        } catch (org.springframework.dao.DataAccessException exception) {
            return new IntegrityConflict(exception);
        } catch (RuntimeException exception) {
            return new UnexpectedFailure(exception);
        }
    }

    private void assertNoCategoryCycle() {
        assertEquals(
                0,
                jdbc.queryForObject(
                        """
        WITH RECURSIVE hierarchy AS (
          SELECT id, parent_id, ARRAY[id] AS path, FALSE AS cycle
          FROM categories
          UNION ALL
          SELECT hierarchy.id, parent.parent_id, hierarchy.path || parent.id,
            parent.id = ANY(hierarchy.path)
          FROM hierarchy
          JOIN categories parent ON parent.id = hierarchy.parent_id
          WHERE hierarchy.parent_id IS NOT NULL AND NOT hierarchy.cycle
        )
        SELECT count(*) FROM hierarchy WHERE cycle
        """,
                        Integer.class));
    }

    private ConcurrencyResult deactivateAfterBarrier(
            DeactivationAttempt attempt, CyclicBarrier barrier) {
        await(barrier);
        try {
            variants.deactivateById(
                    attempt.variantId(),
                    attempt.productId(),
                    attempt.expectedVariantVersion(),
                    attempt.expectedProductVersion());
            return new Success();
        } catch (OptimisticLockingFailureException exception) {
            return new OptimisticConflict(exception);
        } catch (RuntimeException exception) {
            return new UnexpectedFailure(exception);
        }
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrency barrier failed", exception);
        }
    }

    private void assertExactlyOneSuccessAndOneOptimisticConflict(List<ConcurrencyResult> results) {
        assertEquals(1, results.stream().filter(Success.class::isInstance).count());
        assertEquals(1, results.stream().filter(OptimisticConflict.class::isInstance).count());
        assertTrue(results.stream().noneMatch(UnexpectedFailure.class::isInstance));
    }

    private ProductEntity copy(ProductEntity source, String name) {
        return new ProductEntity(
                source.getId(),
                source.getBrandId(),
                name,
                source.getSlug(),
                source.getDescription(),
                source.getStatus(),
                source.getMetadata(),
                source.getVersion(),
                source.getPublishedAt(),
                source.getDeletedAt(),
                source.getCreatedAt(),
                source.getUpdatedAt());
    }

    private sealed interface ConcurrencyResult
            permits Success, OptimisticConflict, IntegrityConflict, UnexpectedFailure {}

    private record Success() implements ConcurrencyResult {}

    private record OptimisticConflict(Throwable cause) implements ConcurrencyResult {}

    private record IntegrityConflict(Throwable cause) implements ConcurrencyResult {}

    private record UnexpectedFailure(Throwable cause) implements ConcurrencyResult {}

    private record DeactivationAttempt(
            UUID variantId,
            UUID productId,
            long expectedVariantVersion,
            long expectedProductVersion) {}
}

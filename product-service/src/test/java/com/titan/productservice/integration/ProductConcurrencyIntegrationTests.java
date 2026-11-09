package com.titan.productservice.integration;

import com.titan.productservice.TestcontainersConfiguration;
import com.titan.productservice.config.ProductDataSourceConfig;
import com.titan.productservice.data.entity.CategoryEntity;
import com.titan.productservice.data.entity.ProductCategoryEntity;
import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.data.entity.ProductMediaEntity;
import com.titan.productservice.data.entity.ProductVariantEntity;
import com.titan.productservice.data.exception.ProductConcurrentModificationException;
import com.titan.productservice.data.exception.ProductDataException;
import com.titan.productservice.data.exception.ProductSQLExceptionTranslator;
import com.titan.productservice.data.repository.BrandRepository;
import com.titan.productservice.data.repository.CategoryRepository;
import com.titan.productservice.data.repository.ProductCategoryRepository;
import com.titan.productservice.data.repository.ProductMediaRepository;
import com.titan.productservice.data.repository.ProductRepository;
import com.titan.productservice.data.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    TestcontainersConfiguration.class,
    BrandRepository.class,
    CategoryRepository.class,
    ProductRepository.class,
    ProductCategoryRepository.class,
    ProductMediaRepository.class,
    ProductVariantRepository.class,
    ProductDataSourceConfig.class,
    ProductSQLExceptionTranslator.class
})
class ProductConcurrencyIntegrationTests {

    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private ProductMediaRepository media;
    @Autowired private ProductCategoryRepository productCategories;
    @Autowired private CategoryRepository categories;
    @Autowired private BrandRepository brands;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update(
                "UPDATE products SET status = 'DRAFT', published_at = NULL WHERE status = 'ACTIVE'");
        jdbc.update("DELETE FROM product_variants");
        jdbc.update("DELETE FROM product_categories");
        jdbc.update("DELETE FROM product_media");
        jdbc.update("DELETE FROM products");
        jdbc.update("UPDATE categories SET parent_id = NULL");
        jdbc.update("DELETE FROM categories");
        jdbc.update("DELETE FROM brands");
    }

    @Test
    void concurrentProductUpdatesHaveOneOptimisticWinner() throws Exception {
        ProductEntity product = product("concurrent-product");
        products.insert(product);

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () -> updateAfterBarrier(copy(product, "first"), barrier),
                        () -> updateAfterBarrier(copy(product, "second"), barrier));

        assertExactlyOneSuccessAndOneOptimisticConflict(results);
        Assertions.assertThat(productVersion(product.getId())).isEqualTo(1);
    }

    @Test
    void concurrentVariantInsertionsUseParentExpectedVersion() throws Exception {
        ProductEntity product = product("concurrent-variants");
        products.insert(product);

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () -> insertVariantAfterBarrier(product.getId(), "a", barrier, 0),
                        () -> insertVariantAfterBarrier(product.getId(), "b", barrier, 0));

        assertExactlyOneSuccessAndOneOptimisticConflict(results);
        Assertions.assertThat(productVersion(product.getId())).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateSkuAcrossDifferentProductsHasOneUniqueWinner() throws Exception {
        ProductEntity firstProduct = product("sku-product-a");
        ProductEntity secondProduct = product("sku-product-b");
        products.insert(firstProduct);
        products.insert(secondProduct);

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () ->
                                insertVariantAfterBarrier(
                                        firstProduct.getId(), "same-sku", barrier, 0),
                        () ->
                                insertVariantAfterBarrier(
                                        secondProduct.getId(), "same-sku", barrier, 0));

        Assertions.assertThat(results.stream().filter(Success.class::isInstance).count())
                .isEqualTo(1);
        Assertions.assertThat(results.stream().filter(IntegrityConflict.class::isInstance).count())
                .isEqualTo(1);
        Assertions.assertThat(results.stream().noneMatch(OptimisticConflict.class::isInstance))
                .isTrue();
        Assertions.assertThat(results.stream().noneMatch(UnexpectedFailure.class::isInstance))
                .isTrue();
        Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM product_variants WHERE LOWER(sku) = LOWER(?)",
                                Integer.class,
                                "same-sku"))
                .isEqualTo(1);
    }

    @Test
    void concurrentPrimaryMediaAssignmentsHaveOneAggregateWinner() throws Exception {
        ProductEntity product = product("primary-media-race");
        products.insert(product);
        ProductMediaEntity first = media(product.getId(), "IMAGE", 0);
        ProductMediaEntity second = media(product.getId(), "IMAGE", 1);

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () -> insertPrimaryMediaAfterBarrier(first, barrier, 0),
                        () -> insertPrimaryMediaAfterBarrier(second, barrier, 0));

        assertExactlyOneSuccessAndOneOptimisticConflict(results);
        Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM product_media WHERE product_id = ? AND is_primary = TRUE",
                                Integer.class,
                                product.getId()))
                .isEqualTo(1);
    }

    @Test
    void concurrentPrimaryCategoryAssignmentsHaveOneAggregateWinner() throws Exception {
        ProductEntity product = product("primary-category-race");
        products.insert(product);
        CategoryEntity first = category("race-category-a");
        CategoryEntity second = category("race-category-b");
        categories.insert(first);
        categories.insert(second);

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () ->
                                insertPrimaryCategoryAfterBarrier(
                                        product.getId(), first.getId(), barrier, 0),
                        () ->
                                insertPrimaryCategoryAfterBarrier(
                                        product.getId(), second.getId(), barrier, 0));

        assertExactlyOneSuccessAndOneOptimisticConflict(results);
        Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM product_categories WHERE product_id = ? AND is_primary = TRUE",
                                Integer.class,
                                product.getId()))
                .isEqualTo(1);
    }

    @Test
    void concurrentCategoryMovesCannotCreateACycle() throws Exception {
        CategoryEntity firstRoot = category("cycle-root-a");
        CategoryEntity secondRoot = category("cycle-root-b");
        categories.insert(firstRoot);
        categories.insert(secondRoot);

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () -> moveCategoryAfterBarrier(firstRoot, secondRoot.getId(), barrier),
                        () -> moveCategoryAfterBarrier(secondRoot, firstRoot.getId(), barrier));

        Assertions.assertThat(results.stream().filter(Success.class::isInstance).count())
                .isEqualTo(1);
        Assertions.assertThat(results.stream().filter(IntegrityConflict.class::isInstance).count())
                .isEqualTo(1);
        Assertions.assertThat(results.stream().noneMatch(UnexpectedFailure.class::isInstance))
                .isTrue();
        assertNoCategoryCycle();
    }

    @Test
    void concurrentDeactivationOfFinalActiveVariantsLeavesOneActive() throws Exception {
        ProductEntity product = product("final-variant-race");
        products.insert(product);
        ProductVariantEntity first = variant(product.getId(), "final-a", BigDecimal.ONE);
        ProductVariantEntity second = variant(product.getId(), "final-b", BigDecimal.valueOf(2));
        variants.insert(first, 0);
        variants.insert(second, 1);
        product.setStatus("ACTIVE");
        product.setPublishedAt(OffsetDateTime.now());
        product.setVersion(2);
        products.update(product);
        long expectedProductVersion = productVersion(product.getId());

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<ConcurrencyResult> results =
                run(
                        () ->
                                deactivateAfterBarrier(
                                        first.getId(),
                                        product.getId(),
                                        0,
                                        expectedProductVersion,
                                        barrier),
                        () ->
                                deactivateAfterBarrier(
                                        second.getId(),
                                        product.getId(),
                                        0,
                                        expectedProductVersion,
                                        barrier));

        assertExactlyOneSuccessAndOneOptimisticConflict(results);
        Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM product_variants WHERE product_id = ? AND status = 'ACTIVE'",
                                Integer.class,
                                product.getId()))
                .isEqualTo(1);
        Assertions.assertThat(products.findById(product.getId()).orElseThrow().getStatus())
                .isEqualTo("ACTIVE");
    }

    private List<ConcurrencyResult> run(
            Callable<ConcurrencyResult> first, Callable<ConcurrencyResult> second)
            throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<ConcurrencyResult>> futures =
                    pool.invokeAll(List.of(first, second), 10, TimeUnit.SECONDS);
            List<ConcurrencyResult> results = futures.stream().map(this::resultOf).toList();
            Assertions.assertThat(results).hasSize(2);
            return results;
        }
    }

    private ConcurrencyResult resultOf(Future<ConcurrencyResult> future) {
        try {
            Assertions.assertThat(future.isCancelled()).isFalse();
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
        } catch (ProductConcurrentModificationException exception) {
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
        } catch (ProductConcurrentModificationException exception) {
            return new OptimisticConflict(exception);
        } catch (ProductDataException exception) {
            return new IntegrityConflict(exception);
        } catch (RuntimeException exception) {
            return new UnexpectedFailure(exception);
        }
    }

    private ConcurrencyResult insertPrimaryMediaAfterBarrier(
            ProductMediaEntity value, CyclicBarrier barrier, long expectedVersion) {
        value.setPrimary(true);
        await(barrier);
        try {
            media.insertReplacingPrimary(value, expectedVersion);
            return new Success();
        } catch (ProductConcurrentModificationException exception) {
            return new OptimisticConflict(exception);
        } catch (ProductDataException exception) {
            return new IntegrityConflict(exception);
        } catch (RuntimeException exception) {
            return new UnexpectedFailure(exception);
        }
    }

    private ConcurrencyResult insertPrimaryCategoryAfterBarrier(
            UUID productId, UUID categoryId, CyclicBarrier barrier, long expectedVersion) {
        await(barrier);
        try {
            productCategories.insertReplacingPrimary(
                    new ProductCategoryEntity(productId, categoryId, true, null), expectedVersion);
            return new Success();
        } catch (ProductConcurrentModificationException exception) {
            return new OptimisticConflict(exception);
        } catch (ProductDataException exception) {
            return new IntegrityConflict(exception);
        } catch (RuntimeException exception) {
            return new UnexpectedFailure(exception);
        }
    }

    private ConcurrencyResult moveCategoryAfterBarrier(
            CategoryEntity value, UUID parentId, CyclicBarrier barrier) {
        value.setParentId(parentId);
        await(barrier);
        try {
            categories.update(value);
            return new Success();
        } catch (ProductDataException exception) {
            return new IntegrityConflict(exception);
        } catch (RuntimeException exception) {
            return new UnexpectedFailure(exception);
        }
    }

    private ConcurrencyResult deactivateAfterBarrier(
            UUID variantId,
            UUID productId,
            long expectedVariantVersion,
            long expectedProductVersion,
            CyclicBarrier barrier) {
        await(barrier);
        try {
            variants.deactivateById(
                    variantId, productId, expectedVariantVersion, expectedProductVersion);
            return new Success();
        } catch (ProductConcurrentModificationException exception) {
            return new OptimisticConflict(exception);
        } catch (ProductDataException exception) {
            return new IntegrityConflict(exception);
        } catch (RuntimeException exception) {
            return new UnexpectedFailure(exception);
        }
    }

    private void assertNoCategoryCycle() {
        Assertions.assertThat(
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
                                Integer.class))
                .isZero();
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrency barrier failed", exception);
        }
    }

    private void assertExactlyOneSuccessAndOneOptimisticConflict(List<ConcurrencyResult> results) {
        Assertions.assertThat(results.stream().filter(Success.class::isInstance).count())
                .isEqualTo(1);
        Assertions.assertThat(results.stream().filter(OptimisticConflict.class::isInstance).count())
                .isEqualTo(1);
        Assertions.assertThat(results.stream().noneMatch(UnexpectedFailure.class::isInstance))
                .isTrue();
    }

    private long productVersion(UUID id) {
        return jdbc.queryForObject("SELECT version FROM products WHERE id = ?", Long.class, id);
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

    private ProductEntity product(String slug) {
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

    private ProductMediaEntity media(UUID productId, String type, int position) {
        ProductMediaEntity value = new ProductMediaEntity();
        value.setId(UUID.randomUUID());
        value.setProductId(productId);
        value.setMediaType(type);
        value.setStorageKey("s3://bucket/" + UUID.randomUUID());
        value.setAltText("alt");
        value.setPosition(position);
        value.setPrimary(false);
        return value;
    }

    private ProductVariantEntity variant(UUID productId, String sku, BigDecimal price) {
        ProductVariantEntity value = new ProductVariantEntity();
        value.setId(UUID.randomUUID());
        value.setProductId(productId);
        value.setSku(sku);
        value.setPrice(price);
        value.setCurrency("INR");
        value.setStatus("ACTIVE");
        value.setAttributes(Map.of());
        return value;
    }

    private CategoryEntity category(String slug) {
        return new CategoryEntity(
                UUID.randomUUID(),
                null,
                "Category " + slug,
                slug,
                null,
                Map.of(),
                0,
                true,
                null,
                null);
    }

    private sealed interface ConcurrencyResult
            permits Success, OptimisticConflict, IntegrityConflict, UnexpectedFailure {}

    private record Success() implements ConcurrencyResult {}

    private record OptimisticConflict(Throwable cause) implements ConcurrencyResult {}

    private record IntegrityConflict(Throwable cause) implements ConcurrencyResult {}

    private record UnexpectedFailure(Throwable cause) implements ConcurrencyResult {}
}

package com.titan.productservice.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.titan.productservice.TestcontainersConfiguration;
import com.titan.productservice.config.ProductCacheProperties;
import com.titan.productservice.data.repository.AbstractRepositoryIntegrationTest;
import com.titan.productservice.entity.ProductChangedEvent;
import com.titan.productservice.entity.ProductEntity;
import com.titan.productservice.entity.ProductSnapshot;
import com.titan.productservice.service.ProductCacheService;
import com.titan.productservice.util.ProductCacheKey;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({TestcontainersConfiguration.class, CacheTestcontainersConfiguration.class})
@TestPropertySource(
        properties = {
            "titan.cache.l2-enabled=true",
            "spring.data.redis.timeout=2s",
            "titan.cache.ttl-jitter=0ms"
        })
class ProductCacheIntegrationTests extends AbstractRepositoryIntegrationTest {
    @Autowired private ProductCacheService cache;
    @Autowired private ProductCacheProperties properties;
    @Autowired private StringRedisTemplate redis;

    @BeforeEach
    void clearCache() {
        cache.clearLocal();
        cache.setBeforeL1PutHook(null);
        cache.setBeforeRemoteWriteHook(null);
    }

    @Test
    void firstReadLoadsPostgresAndSecondReadUsesL1() {
        var product = activeProduct("cache-l1", "cache-l1-sku");
        double hitsBefore = cache.l1Hits();

        assertEquals(product.getId(), cache.findActiveById(product.getId()).orElseThrow().id());
        double loadsAfterFirst = cache.databaseLoads();
        assertEquals(product.getId(), cache.findActiveById(product.getId()).orElseThrow().id());

        assertEquals(loadsAfterFirst, cache.databaseLoads());
        assertEquals(hitsBefore + 1, cache.l1Hits());
    }

    @Test
    void l1MissReadsRedisAndNegativeEntriesAreBounded() {
        double loadsBefore = cache.databaseLoads();
        var product = activeProduct("cache-l2", "cache-l2-sku");
        assertTrue(cache.findActiveById(product.getId()).isPresent());
        cache.clearLocal();

        assertTrue(cache.findActiveById(product.getId()).isPresent());
        assertEquals(
                loadsBefore + 1,
                cache.databaseLoads(),
                () -> "loads before=" + loadsBefore + ", after=" + cache.databaseLoads());

        UUID missing = UUID.randomUUID();
        assertFalse(cache.findActiveById(missing).isPresent());
        cache.clearLocal();
        assertFalse(cache.findActiveById(missing).isPresent());
        assertEquals(loadsBefore + 2, cache.databaseLoads());
    }

    @Test
    void concurrentSameKeyMissesUseOneDatabaseLoad() throws Exception {
        double loadsBefore = cache.databaseLoads();
        var product = activeProduct("cache-single-flight", "single-flight-sku");
        cache.clearLocal();

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            var gate = new CountDownLatch(1);
            var futures =
                    java.util.stream.IntStream.range(0, 8)
                            .mapToObj(
                                    ignored ->
                                            executor.submit(
                                                    () -> {
                                                        gate.await(5, TimeUnit.SECONDS);
                                                        return cache.findActiveById(
                                                                product.getId());
                                                    }))
                            .toList();
            gate.countDown();
            assertEquals(8, futures.stream().filter(future -> get(future).isPresent()).count());
        }
        assertEquals(loadsBefore + 1, cache.databaseLoads());
    }

    @Test
    void joiningCallersReleaseTheirFenceStateAfterOwnerCompletes() throws Exception {
        var product = activeProduct("cache-join-cleanup", "join-cleanup-sku");
        cache.clearLocal();
        String key = ProductCacheKey.byId(properties, product.getId());
        CountDownLatch ownerReachedPut = new CountDownLatch(1);
        CountDownLatch allowOwnerToComplete = new CountDownLatch(1);
        cache.setBeforeL1PutHook(
                () -> {
                    ownerReachedPut.countDown();
                    await(allowOwnerToComplete);
                });

        int callers = 8;
        CyclicBarrier start = new CyclicBarrier(callers);
        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            var futures =
                    java.util.stream.IntStream.range(0, callers)
                            .mapToObj(
                                    ignored ->
                                            executor.submit(
                                                    () -> {
                                                        start.await(5, TimeUnit.SECONDS);
                                                        return cache.findActiveById(
                                                                product.getId());
                                                    }))
                            .toList();
            assertTrue(ownerReachedPut.await(5, TimeUnit.SECONDS));
            assertTrue(cache.localFenceCount(key) >= 1);
            allowOwnerToComplete.countDown();
            assertEquals(
                    callers, futures.stream().filter(future -> get(future).isPresent()).count());
        }

        cache.setBeforeL1PutHook(null);
        assertEquals(0, cache.localFenceCount(key));
    }

    @Test
    void joiningCallersReleaseTheirFenceStateAfterOwnerFails() throws Exception {
        UUID id = UUID.randomUUID();
        String key = ProductCacheKey.byId(properties, id);
        CountDownLatch ownerReachedWrite = new CountDownLatch(1);
        CountDownLatch allowOwnerToFail = new CountDownLatch(1);
        cache.setBeforeRemoteWriteHook(
                () -> {
                    ownerReachedWrite.countDown();
                    await(allowOwnerToFail);
                    throw new IllegalStateException("deterministic cache-load failure");
                });

        int callers = 8;
        CyclicBarrier start = new CyclicBarrier(callers);
        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            var futures =
                    java.util.stream.IntStream.range(0, callers)
                            .mapToObj(
                                    ignored ->
                                            executor.submit(
                                                    () -> {
                                                        start.await(5, TimeUnit.SECONDS);
                                                        return cache.findActiveById(id);
                                                    }))
                            .toList();
            assertTrue(ownerReachedWrite.await(5, TimeUnit.SECONDS));
            assertTrue(cache.localFenceCount(key) >= 1);
            allowOwnerToFail.countDown();
            futures.forEach(
                    future -> {
                        try {
                            future.get(5, TimeUnit.SECONDS);
                            throw new AssertionError("Expected cache load failure");
                        } catch (java.util.concurrent.ExecutionException expected) {
                            Throwable cause = expected.getCause();
                            while (cause.getCause() != null) {
                                cause = cause.getCause();
                            }
                            assertTrue(cause instanceof IllegalStateException);
                        } catch (Exception exception) {
                            throw new AssertionError(
                                    "Cache task did not complete deterministically", exception);
                        }
                    });
        }

        cache.setBeforeRemoteWriteHook(null);
        assertEquals(0, cache.localFenceCount(key));
    }

    @Test
    void versionedInvalidationEvictsL1AndDoesNotServeOldValue() {
        var product = activeProduct("cache-invalidate", "invalidate-sku");
        cache.findActiveById(product.getId()).orElseThrow();
        product.setName("new-name");
        product.setVersion(2);
        products.update(product);

        assertEquals("new-name", cache.findActiveById(product.getId()).orElseThrow().name());
    }

    @Test
    void differentKeysCanLoadIndependently() throws Exception {
        double loadsBefore = cache.databaseLoads();
        var first = activeProduct("cache-independent-a", "independent-a");
        var second = activeProduct("cache-independent-b", "independent-b");
        cache.clearLocal();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var futures =
                    List.of(
                            executor.submit(() -> cache.findActiveById(first.getId())),
                            executor.submit(() -> cache.findActiveById(second.getId())));
            assertTrue(get(futures.get(0)).isPresent());
            assertTrue(get(futures.get(1)).isPresent());
        }
    }

    @Test
    void staleIdLoaderCannotRepopulateL1AfterCreationInvalidation() throws Exception {
        double loadsBefore = cache.databaseLoads();
        UUID id = UUID.randomUUID();
        String key = ProductCacheKey.byId(properties, id);
        redis.delete(List.of(key, ProductCacheKey.fence(properties, id)));
        CountDownLatch beforePut = new CountDownLatch(1);
        CountDownLatch continuePut = new CountDownLatch(1);
        cache.setBeforeL1PutHook(
                () -> {
                    beforePut.countDown();
                    await(continuePut);
                });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var staleRead = executor.submit(() -> cache.findActiveById(id));
            assertTrue(beforePut.await(5, TimeUnit.SECONDS));

            ProductEntity created = productWithId(id, "race-id");
            products.insert(created);
            variants.insert(variant(id, "race-id-sku", java.math.BigDecimal.ONE), 0);
            created.setStatus("ACTIVE");
            created.setPublishedAt(OffsetDateTime.now());
            created.setVersion(1);
            products.update(created);
            continuePut.countDown();
            assertTrue(get(staleRead).isEmpty());
        }

        cache.setBeforeL1PutHook(null);
        assertTrue(cache.findActiveById(id).isPresent());
        assertEquals(loadsBefore + 2, cache.databaseLoads());
    }

    @Test
    void staleSlugLoaderCannotRepopulateNegativeCacheAfterCreation() throws Exception {
        double loadsBefore = cache.databaseLoads();
        String slug = "race-slug";
        String key = ProductCacheKey.bySlug(properties, slug);
        redis.delete(List.of(key, ProductCacheKey.lookupFence(properties, key)));
        CountDownLatch beforePut = new CountDownLatch(1);
        CountDownLatch continuePut = new CountDownLatch(1);
        cache.setBeforeL1PutHook(
                () -> {
                    beforePut.countDown();
                    await(continuePut);
                });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var staleRead = executor.submit(() -> cache.findBySlug(slug));
            assertTrue(beforePut.await(5, TimeUnit.SECONDS));

            ProductEntity created = productWithId(UUID.randomUUID(), slug);
            products.insert(created);
            variants.insert(variant(created.getId(), "race-slug-sku", java.math.BigDecimal.ONE), 0);
            created.setStatus("ACTIVE");
            created.setPublishedAt(OffsetDateTime.now());
            created.setVersion(1);
            products.update(created);
            continuePut.countDown();
            assertTrue(get(staleRead).isEmpty());
        }

        cache.setBeforeL1PutHook(null);
        assertTrue(cache.findBySlug(slug).isPresent());
        assertEquals(loadsBefore + 2, cache.databaseLoads());
    }

    @Test
    void outOfOrderInvalidationCannotLowerRedisFence() {
        UUID id = UUID.randomUUID();
        cache.evictProduct(new ProductChangedEvent(id, null, "ordered", 12, "UPDATED"));
        cache.evictProduct(new ProductChangedEvent(id, null, "ordered", 11, "UPDATED"));

        assertEquals("12", redis.opsForValue().get(ProductCacheKey.fence(properties, id)));
        assertEquals(
                "12",
                redis.opsForValue()
                        .get(
                                ProductCacheKey.lookupFence(
                                        properties,
                                        ProductCacheKey.bySlug(properties, "ordered"))));
    }

    @Test
    void snapshotDeepCopiesNestedJsonIncludingNull() {
        Map<String, Object> nested = new HashMap<>();
        List<Object> values = new java.util.ArrayList<>();
        values.add("before");
        values.add(null);
        nested.put("list", values);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("nested", nested);
        ProductEntity entity = product("immutable");
        entity.setMetadata(metadata);

        ProductSnapshot snapshot = ProductSnapshot.from(entity);
        values.set(0, "after");
        assertEquals(
                "before",
                ((List<?>) ((Map<?, ?>) snapshot.metadata().get("nested")).get("list")).get(0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> ((Map<String, Object>) snapshot.metadata()).put("new", true));
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        ((List<Object>) ((Map<?, ?>) snapshot.metadata().get("nested")).get("list"))
                                .add("blocked"));
    }

    @Test
    void l2MetricsOnlyCountActualEnabledRemoteOperations() {
        double missesBefore = cache.l2Misses();
        double hitsBefore = cache.l2Hits();
        cache.clearLocal();
        UUID id = UUID.randomUUID();
        redis.delete(
                List.of(
                        ProductCacheKey.byId(properties, id),
                        ProductCacheKey.fence(properties, id)));

        assertTrue(cache.findActiveById(id).isEmpty());
        assertEquals(missesBefore + 1, cache.l2Misses());
        assertEquals(hitsBefore, cache.l2Hits());

        cache.clearLocal();
        assertTrue(cache.findActiveById(id).isEmpty());
        assertEquals(missesBefore + 1, cache.l2Misses());
        assertEquals(hitsBefore + 1, cache.l2Hits());
    }

    @Test
    void malformedRedisPayloadCountsFailureWithoutCountingMiss() {
        UUID id = UUID.randomUUID();
        String key = ProductCacheKey.byId(properties, id);
        redis.opsForValue().set(key, "{not-json");
        double failuresBefore = cache.cacheFailures();
        double missesBefore = cache.l2Misses();

        assertTrue(cache.findActiveById(id).isEmpty());

        assertEquals(failuresBefore + 1, cache.cacheFailures());
        assertEquals(missesBefore, cache.l2Misses());
    }

    @Test
    void versionZeroIdInvalidationUsesGenerationNotVersionComparison() throws Exception {
        UUID id = UUID.randomUUID();
        String key = ProductCacheKey.byId(properties, id);
        String fenceKey = ProductCacheKey.fence(properties, id);
        redis.delete(List.of(key, fenceKey, ProductCacheKey.generation(properties, fenceKey)));
        CountDownLatch beforeWrite = new CountDownLatch(1);
        CountDownLatch continueWrite = new CountDownLatch(1);
        cache.setBeforeRemoteWriteHook(
                () -> {
                    beforeWrite.countDown();
                    await(continueWrite);
                });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var staleRead = executor.submit(() -> cache.findActiveById(id));
            assertTrue(beforeWrite.await(5, TimeUnit.SECONDS));
            cache.evictProduct(new ProductChangedEvent(id, null, null, 0, "CREATED"));
            continueWrite.countDown();
            assertTrue(get(staleRead).isEmpty());
        }

        cache.setBeforeRemoteWriteHook(null);
        assertEquals(
                "1", redis.opsForValue().get(ProductCacheKey.generation(properties, fenceKey)));
        assertTrue(redis.opsForValue().get(key) == null);
        assertTrue(cache.findActiveById(id).isEmpty());
    }

    @Test
    void versionZeroSlugInvalidationPreventsStaleNegativeRedisEntry() throws Exception {
        String slug = "version-zero-slug";
        String key = ProductCacheKey.bySlug(properties, slug);
        String fenceKey = ProductCacheKey.lookupFence(properties, key);
        redis.delete(List.of(key, fenceKey, ProductCacheKey.generation(properties, fenceKey)));
        CountDownLatch beforeWrite = new CountDownLatch(1);
        CountDownLatch continueWrite = new CountDownLatch(1);
        cache.setBeforeRemoteWriteHook(
                () -> {
                    beforeWrite.countDown();
                    await(continueWrite);
                });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var staleRead = executor.submit(() -> cache.findBySlug(slug));
            assertTrue(beforeWrite.await(5, TimeUnit.SECONDS));
            cache.evictProduct(
                    new ProductChangedEvent(UUID.randomUUID(), null, slug, 0, "CREATED"));
            continueWrite.countDown();
            assertTrue(get(staleRead).isEmpty());
        }

        cache.setBeforeRemoteWriteHook(null);
        assertEquals(
                "1", redis.opsForValue().get(ProductCacheKey.generation(properties, fenceKey)));
        assertTrue(redis.opsForValue().get(key) == null);
    }

    @Test
    void localFenceStateIsReleasedAfterLoadAndConcurrentInvalidation() throws Exception {
        UUID id = UUID.randomUUID();
        String key = ProductCacheKey.byId(properties, id);
        CountDownLatch beforePut = new CountDownLatch(1);
        CountDownLatch continuePut = new CountDownLatch(1);
        cache.setBeforeL1PutHook(
                () -> {
                    beforePut.countDown();
                    await(continuePut);
                });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var read = executor.submit(() -> cache.findActiveById(id));
            assertTrue(beforePut.await(5, TimeUnit.SECONDS));
            cache.evictProduct(new ProductChangedEvent(id, null, null, 0, "CREATED"));
            assertEquals(1, cache.localFenceCount(key));
            continuePut.countDown();
            assertTrue(get(read).isEmpty());
        }

        cache.setBeforeL1PutHook(null);
        assertEquals(0, cache.localFenceCount(key));
    }

    @Test
    void expiredRedisVersionFenceCannotAdmitOldLoaderAfterGenerationInvalidation()
            throws Exception {
        UUID id = UUID.randomUUID();
        String key = ProductCacheKey.byId(properties, id);
        String fenceKey = ProductCacheKey.fence(properties, id);
        redis.delete(List.of(key, fenceKey, ProductCacheKey.generation(properties, fenceKey)));
        CountDownLatch beforeWrite = new CountDownLatch(1);
        CountDownLatch continueWrite = new CountDownLatch(1);
        cache.setBeforeRemoteWriteHook(
                () -> {
                    beforeWrite.countDown();
                    await(continueWrite);
                });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var staleRead = executor.submit(() -> cache.findActiveById(id));
            assertTrue(beforeWrite.await(5, TimeUnit.SECONDS));
            cache.evictProduct(new ProductChangedEvent(id, null, null, 0, "UPDATED"));
            redis.delete(fenceKey);
            continueWrite.countDown();
            assertTrue(get(staleRead).isEmpty());
        }

        cache.setBeforeRemoteWriteHook(null);
        assertEquals(
                "1", redis.opsForValue().get(ProductCacheKey.generation(properties, fenceKey)));
        assertTrue(redis.opsForValue().get(key) == null);
    }

    @Test
    void snapshotToEntityDoesNotShareNestedMetadataReferences() {
        Map<String, Object> nested = new HashMap<>();
        List<Object> values = new java.util.ArrayList<>();
        values.add("before");
        nested.put("list", values);
        ProductEntity entity = product("entity-copy");
        entity.setMetadata(Map.of("nested", nested));

        ProductSnapshot snapshot = ProductSnapshot.from(entity);
        ProductEntity converted = snapshot.toEntity();
        values.set(0, "source-mutated");
        ((List<Object>) ((Map<?, ?>) converted.getMetadata().get("nested")).get("list"))
                .set(0, "entity-mutated");

        assertEquals(
                "before",
                ((List<?>) ((Map<?, ?>) snapshot.metadata().get("nested")).get("list")).get(0));
        assertEquals(
                "before",
                ((List<?>) ((Map<?, ?>) snapshot.metadata().get("nested")).get("list")).get(0));
    }

    private com.titan.productservice.entity.ProductEntity activeProduct(String slug, String sku) {
        var product = product(slug);
        products.insert(product);
        variants.insert(variant(product.getId(), sku, java.math.BigDecimal.ONE), 0);
        product.setStatus("ACTIVE");
        product.setPublishedAt(java.time.OffsetDateTime.now());
        product.setVersion(1);
        products.update(product);
        return product;
    }

    private ProductEntity productWithId(UUID id, String slug) {
        return new ProductEntity(
                id,
                null,
                "Product " + slug,
                slug,
                null,
                "DRAFT",
                Map.of(),
                0,
                null,
                null,
                null,
                null);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while coordinating test", exception);
        }
    }

    private static <T> T get(java.util.concurrent.Future<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Cache task failed", exception);
        }
    }
}

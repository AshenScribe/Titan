package com.titan.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.titan.productservice.config.ProductCacheProperties;
import com.titan.productservice.data.entity.ProductCacheEnvelope;
import com.titan.productservice.data.entity.ProductChangedEvent;
import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.data.entity.ProductSnapshot;
import com.titan.productservice.data.repository.ProductRepository;
import com.titan.productservice.util.ProductCacheKey;
import com.titan.productservice.util.ProductCacheLookup;
import com.titan.productservice.util.ProductCacheScripts;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class ProductCacheService {
    private static final Logger LOG = LoggerFactory.getLogger(ProductCacheService.class);
    private static final int LOCK_STRIPES = 64;

    private final ProductRepository repository;
    private final ProductCacheProperties properties;
    private final Cache<String, ProductCacheEnvelope> l1;
    private final Cache<UUID, Boolean> processedInvalidations;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Counter l1Hits;
    private final Counter l1Misses;
    private final Counter l2Hits;
    private final Counter l2Misses;
    private final Counter databaseLoads;
    private final Counter cacheFailures;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<ProductSnapshot>>> inFlight =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalFenceState> localFences =
            new ConcurrentHashMap<>();
    private final Object[] locks = new Object[LOCK_STRIPES];

    // Test hooks
    private volatile Runnable beforeL1PutHook = () -> {};
    private volatile Runnable beforeRemoteWriteHook = () -> {};

    public ProductCacheService(
            ProductRepository repository,
            ProductCacheProperties properties,
            Cache<String, ProductCacheEnvelope> l1,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.properties = properties;
        this.l1 = l1;
        processedInvalidations =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofHours(1))
                        .build();
        this.redis = redis;
        this.objectMapper = objectMapper;
        l1Hits = Counter.builder("titan.cache.l1.hits").register(meterRegistry);
        l1Misses = Counter.builder("titan.cache.l1.misses").register(meterRegistry);
        l2Hits = Counter.builder("titan.cache.l2.hits").register(meterRegistry);
        l2Misses = Counter.builder("titan.cache.l2.misses").register(meterRegistry);
        databaseLoads = Counter.builder("titan.cache.database.loads").register(meterRegistry);
        cacheFailures = Counter.builder("titan.cache.failures").register(meterRegistry);
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
    }

    public Optional<ProductSnapshot> findActiveById(UUID id) {
        ProductCacheLookup lookup =
                new ProductCacheLookup(
                        ProductCacheKey.byId(properties, id),
                        id,
                        ProductCacheKey.fence(properties, id));
        return load(lookup, () -> repository.findActiveById(id));
    }

    public Optional<ProductSnapshot> findBySlug(String slug) {
        String key = ProductCacheKey.bySlug(properties, slug);
        return load(
                new ProductCacheLookup(key, null, ProductCacheKey.lookupFence(properties, key)),
                () -> repository.findBySlug(slug));
    }

    public void evictProduct(ProductChangedEvent event) {
        UUID id = event.productId();
        String previousSlug = event.previousSlug();
        String currentSlug = event.currentSlug();
        long version = event.version();
        evictLocal(event);
        if (!properties.isL2Enabled()) {
            return;
        }
        try {
            List<String> keys = new ArrayList<>();
            addInvalidationKey(
                    keys,
                    ProductCacheKey.byId(properties, id),
                    ProductCacheKey.fence(properties, id));
            if (previousSlug != null) {
                String key = ProductCacheKey.bySlug(properties, previousSlug);
                addInvalidationKey(keys, key, ProductCacheKey.lookupFence(properties, key));
            }
            if (currentSlug != null) {
                String key = ProductCacheKey.bySlug(properties, currentSlug);
                addInvalidationKey(keys, key, ProductCacheKey.lookupFence(properties, key));
            }
            redis.execute(
                    ProductCacheScripts.INVALIDATE,
                    keys,
                    Long.toString(version),
                    Long.toString(properties.getL2Ttl().toMillis()));
            if (properties.isInvalidationEnabled()) {
                redis.convertAndSend(
                        properties.getInvalidationChannel(),
                        objectMapper.writeValueAsString(event));
            }
        } catch (RuntimeException | JsonProcessingException exception) {
            cacheFailures.increment();
            LOG.warn("Product cache invalidation failed for product { }", id, exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductChanged(ProductChangedEvent event) {
        evictProduct(event);
    }

    public void clearLocal() {
        l1.invalidateAll();
    }

    void evictLocal(UUID id, String previousSlug, String currentSlug) {
        evictLocal(id, previousSlug, currentSlug, Long.MAX_VALUE);
    }

    void evictLocal(ProductChangedEvent event) {
        if (processedInvalidations.asMap().putIfAbsent(event.eventId(), Boolean.TRUE) != null) {
            return;
        }
        evictLocal(event.productId(), event.previousSlug(), event.currentSlug(), event.version());
    }

    void evictLocal(UUID id, String previousSlug, String currentSlug, long version) {
        invalidateLocal(ProductCacheKey.byId(properties, id), version);
        if (previousSlug != null) {
            invalidateLocal(ProductCacheKey.bySlug(properties, previousSlug), version);
        }
        if (currentSlug != null) {
            invalidateLocal(ProductCacheKey.bySlug(properties, currentSlug), version);
        }
    }

    void recordFailure() {
        cacheFailures.increment();
    }

    public long l1EstimatedSize() {
        return l1.estimatedSize();
    }

    public double databaseLoads() {
        return databaseLoads.count();
    }

    public double l1Hits() {
        return l1Hits.count();
    }

    public double l1Misses() {
        return l1Misses.count();
    }

    public double l2Hits() {
        return l2Hits.count();
    }

    public double l2Misses() {
        return l2Misses.count();
    }

    public double cacheFailures() {
        return cacheFailures.count();
    }

    int localFenceCount() {
        return localFences.size();
    }

    public int localFenceCount(String key) {
        return localFences.containsKey(key) ? 1 : 0;
    }

    private Optional<ProductSnapshot> load(
            ProductCacheLookup lookup, Supplier<Optional<ProductEntity>> loader) {
        LocalFenceState state = acquireFenceState(lookup.key());
        long generation;
        synchronized (state) {
            generation = state.generation;
        }
        Optional<ProductSnapshot> local = readLocal(lookup.key());
        if (local != null) {
            releaseFenceState(lookup.key(), state);
            return local;
        }
        CompletableFuture<Optional<ProductSnapshot>> created = new CompletableFuture<>();
        CompletableFuture<Optional<ProductSnapshot>> existing =
                inFlight.putIfAbsent(lookup.key(), created);
        if (existing != null) {
            try {
                return join(existing);
            } finally {
                releaseFenceState(lookup.key(), state);
            }
        }
        try {
            Optional<ProductCacheEnvelope> remote = readRemote(lookup.key(), lookup.fenceKey());
            Optional<ProductSnapshot> result =
                    remote.map(this::toOptional)
                            .orElseGet(
                                    () ->
                                            loadDatabase(
                                                    lookup,
                                                    currentRedisGeneration(lookup.fenceKey()),
                                                    loader));
            populateLocal(lookup, generation, state, result);
            created.complete(result);
            return result;
        } catch (RuntimeException exception) {
            created.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(lookup.key(), created);
            releaseFenceState(lookup.key(), state);
        }
    }

    private Optional<ProductSnapshot> readLocal(String key) {
        if (!properties.isL1Enabled()) {
            return null;
        }
        synchronized (lockFor(key)) {
            ProductCacheEnvelope local = l1.getIfPresent(key);
            if (local != null) {
                l1Hits.increment();
                return toOptional(local);
            }
            l1Misses.increment();
            return null;
        }
    }

    private void populateLocal(
            ProductCacheLookup lookup,
            long generation,
            LocalFenceState state,
            Optional<ProductSnapshot> result) {
        if (!properties.isL1Enabled()) {
            return;
        }
        beforeL1PutHook.run();
        synchronized (lockFor(lookup.key())) {
            synchronized (state) {
                if (isCurrentLocalState(lookup.key(), state, generation)) {
                    l1.put(
                            lookup.key(),
                            result.map(
                                            value ->
                                                    ProductCacheEnvelope.found(value)
                                                            .withGeneration(generation))
                                    .orElseGet(
                                            () ->
                                                    ProductCacheEnvelope.missing(
                                                            0,
                                                            lookup.productId(),
                                                            lookup.fenceKey(),
                                                            generation)));
                }
            }
        }
    }

    private Optional<ProductSnapshot> loadDatabase(
            ProductCacheLookup lookup, long generation, Supplier<Optional<ProductEntity>> loader) {
        databaseLoads.increment();
        Optional<ProductSnapshot> result = loader.get().map(ProductSnapshot::from);
        if (properties.isL2Enabled() && result.isPresent()) {
            beforeRemoteWriteHook.run();
            writeRemote(
                    lookup.key(),
                    ProductCacheEnvelope.found(result.get()).withGeneration(generation),
                    lookup.fenceKey(),
                    generation);
        } else if (properties.isL2Enabled()) {
            beforeRemoteWriteHook.run();
            writeRemote(
                    lookup.key(),
                    ProductCacheEnvelope.missing(
                            0, lookup.productId(), lookup.fenceKey(), generation),
                    lookup.fenceKey(),
                    generation);
        }
        return result;
    }

    private Optional<ProductCacheEnvelope> readRemote(String key, String fenceKey) {
        if (!properties.isL2Enabled()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                l2Misses.increment();
                return Optional.empty();
            }
            ProductCacheEnvelope envelope =
                    objectMapper.readValue(json, ProductCacheEnvelope.class);
            if (!isCurrent(envelope, fenceKey)) {
                l2Misses.increment();
                return Optional.empty();
            }
            l2Hits.increment();
            return Optional.of(envelope);
        } catch (Exception exception) {
            cacheFailures.increment();
            LOG.warn("Product cache read failed for key { }", key, exception);
            return Optional.empty();
        }
    }

    private boolean isCurrent(ProductCacheEnvelope envelope, String lookupFenceKey) {
        String fenceKey = envelope.fenceKey() == null ? lookupFenceKey : envelope.fenceKey();
        String fence = redis.opsForValue().get(fenceKey);
        String generation =
                redis.opsForValue().get(ProductCacheKey.generation(properties, fenceKey));
        return (fence == null || Long.parseLong(fence) <= envelope.version())
                && (generation == null || Long.parseLong(generation) == envelope.generation());
    }

    private long currentRedisGeneration(String fenceKey) {
        if (!properties.isL2Enabled()) {
            return 0;
        }
        String value = redis.opsForValue().get(ProductCacheKey.generation(properties, fenceKey));
        return value == null ? 0 : Long.parseLong(value);
    }

    private void writeRemote(
            String key, ProductCacheEnvelope envelope, String fenceKey, long generation) {
        try {
            Duration ttl = envelope.found() ? properties.getL2Ttl() : properties.getNegativeTtl();
            long jitter = properties.getTtlJitter().toMillis();
            long ttlMillis =
                    Math.max(
                            1,
                            ttl.toMillis()
                                    + (jitter == 0
                                            ? 0
                                            : ThreadLocalRandom.current()
                                                    .nextLong(-jitter, jitter + 1)));
            String json = objectMapper.writeValueAsString(envelope);
            String generationKey = ProductCacheKey.generation(properties, fenceKey);
            redis.execute(
                    ProductCacheScripts.VERSIONED_WRITE,
                    java.util.List.of(key, fenceKey, generationKey),
                    json,
                    Long.toString(ttlMillis),
                    Long.toString(envelope.version()),
                    Long.toString(generation));
        } catch (Exception exception) {
            cacheFailures.increment();
            LOG.warn("Product cache write failed for key { }", key, exception);
        }
    }

    private Optional<ProductSnapshot> toOptional(ProductCacheEnvelope envelope) {
        return envelope.found() ? Optional.of(envelope.product()) : Optional.empty();
    }

    private void addInvalidationKey(List<String> keys, String valueKey, String fenceKey) {
        keys.add(valueKey);
        keys.add(fenceKey);
        keys.add(ProductCacheKey.generation(properties, fenceKey));
    }

    public void setBeforeL1PutHook(Runnable hook) {
        beforeL1PutHook = hook == null ? () -> {} : hook;
    }

    public void setBeforeRemoteWriteHook(Runnable hook) {
        beforeRemoteWriteHook = hook == null ? () -> {} : hook;
    }

    private void invalidateLocal(String key, long version) {
        LocalFenceState state = localFences.computeIfAbsent(key, ignored -> new LocalFenceState());
        synchronized (lockFor(key)) {
            synchronized (state) {
                state.generation++;
                ProductCacheEnvelope current = l1.getIfPresent(key);
                if (current == null || current.version() <= version) {
                    l1.invalidate(key);
                }
                if (state.users == 0) {
                    state.retired = true;
                    localFences.remove(key, state);
                }
            }
        }
    }

    private LocalFenceState acquireFenceState(String key) {
        LocalFenceState state =
                localFences.compute(
                        key,
                        (ignored, current) -> {
                            LocalFenceState value =
                                    current == null ? new LocalFenceState() : current;
                            synchronized (value) {
                                value.users++;
                            }
                            return value;
                        });
        return state;
    }

    private void releaseFenceState(String key, LocalFenceState state) {
        synchronized (state) {
            state.users--;
            if (state.users == 0) {
                state.retired = true;
                localFences.remove(key, state);
            }
        }
    }

    private boolean isCurrentLocalState(String key, LocalFenceState state, long generation) {
        synchronized (state) {
            return !state.retired
                    && localFences.get(key) == state
                    && state.generation == generation;
        }
    }

    private Object lockFor(String key) {
        return locks[Math.floorMod(key.hashCode(), locks.length)];
    }

    private static final class LocalFenceState {
        private long generation;
        private int users;
        private boolean retired;
    }

    private Optional<ProductSnapshot> join(CompletableFuture<Optional<ProductSnapshot>> future) {
        try {
            return future.join();
        } catch (RuntimeException exception) {
            throw exception;
        }
    }
}

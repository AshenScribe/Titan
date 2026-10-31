# Product cache architecture

The product catalog uses cache-aside storage with PostgreSQL as the authority,
Caffeine as a bounded per-instance L1 cache, and Redis as an optional shared L2
cache. `ProductCacheService` returns immutable `ProductSnapshot` values rather
than mutable persistence entities.

## Read and write behavior

An ID or slug lookup checks L1, then Redis, then PostgreSQL. Positive values and
short-lived not-found envelopes are serialized as JSON. Redis writes use a Lua
compare-before-write operation, a monotonic version fence, and a non-expiring
per-key generation counter. ID negatives use the product fence; slug negatives
use a lookup fence advanced by product events. A loader captures the generation
before its database read, and Redis accepts its write only if that generation
still matches. This prevents version-zero invalidations and also prevents an
old loader from becoming valid merely because an expiring numeric fence has
disappeared. TTL jitter prevents all entries loaded together from expiring
together.

Product repository mutations publish `ProductChangedEvent`. The cache listener
advances each value/fence/generation tuple with one Redis Lua operation, deletes
only when the event is at least as new as the stored fence, and publishes
ID/slug invalidation after a successful transaction commit. There is a failure
window after the Redis Lua operation succeeds but before Pub/Sub publication
succeeds; another instance can miss the notification and retain its L1 value
until its bounded expiry. Pub/Sub is best effort and non-durable. Local L1
eviction uses a reference-counted per-key generation state coordinated with
population; retired state is removed only after no loader references it, and
stale loaders must still match both the state identity and generation. Child
mutations advance the product aggregate version and therefore publish the same
event. A rolled-back transaction does not deliver the after-commit listener.

## Consistency and failure model

Redis is an optimization, not an availability dependency. Redis read, write,
serialization, and invalidation failures fall back to PostgreSQL or local
eviction and are counted in metrics. L1 expiration bounds the lifetime of a
missed invalidation.

This implementation does not claim strict cross-system consistency: PostgreSQL
and Redis do not share a transaction, Redis Pub/Sub is not durable, and a
subscriber can miss an event. The guarantee is bounded best-effort
cross-instance invalidation: Redis generation checks prevent stale Redis
repopulation, while a missed L1 event can remain visible only until the
configured L1 expiry. Durable invalidation should be added through a
transactional outbox when the event pipeline milestone is implemented. Until
then, generation tokens, bounded TTLs, and after-commit publication reduce—but
do not eliminate—the stale-read window.

## Configuration and observability

Settings live under `titan.cache`. L1 has a bounded maximum size and expiry;
L2, invalidation, and distributed locking can be enabled independently.
Micrometer counters expose L1/L2 hits and misses, PostgreSQL fallback loads,
and cache failures without product-ID labels.

The current focused implementation provides local single-flight loading,
negative caching, version-aware Redis writes, after-commit invalidation, and
TTL jitter. Durable Pub/Sub recovery, distributed locking, refresh-ahead, and
stale-while-revalidate remain explicit extension points rather than pretending
to provide guarantees that are not implemented.

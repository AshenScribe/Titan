# AGENTS.md: Titan

Microservices e-commerce backend. Goal: correctness under failure, not feature count.
Solo dev, 8 GB RAM laptop. Depth over breadth; a small system that is provably
correct beats a big one that only works on the happy path.

## Invariants (never break)
1. An accepted order is never lost, even if one component crashes.
2. An event is never processed twice in effect (idempotent consumers; delivery is at-least-once).
3. Stock never goes below zero.
4. A client retry never double-charges (idempotency keys).
5. A service never touches another service's database.
6. No secrets or personal data in logs or prompts.
7. Orders take price and name from product-service (snapshot at order time), never from search results.

## Services
| Service           | Role               | Owns                                                                       | Status      |
|-------------------|--------------------|----------------------------------------------------------------------------|-------------|
| product-service   | Write side         | Catalog (Postgres, source of truth); emits `product.updated.v1` via outbox | Built       |
| order-service     | Order lifecycle    | Orders, outbox, saga state                                                 | Built       |
| inventory-service | Stock              | Stock counts, reservations, expiry                                         | Not started |
| payment-service   | Simulated payments | Payment attempts, idempotency keys                                         | Not started |
| gateway           | Edge               | Routing, JWT validation, rate limiting                                     | <fill in>   |
| auth-service      | Auth               | Users, JWT, MFA                                                            | Not started |

## Decisions already made (do not reopen without asking)
- Spring Boot MVC with virtual threads (`spring.threads.virtual.enabled=true`); no WebFlux
  in services. Gateway may be reactive. Netty only in Valoq.
- CQRS split: product-service = write side, search-service = read side.
- Elasticsearch is a read model only; never used for stock or price checks.
- gRPC for synchronous service-to-service calls; Kafka for events.
- Events go through the transactional outbox + Debezium. No dual writes.
- Caching only on the read-heavy catalog path (cache-aside, TTL with jitter,
  single-flight, version-stamped values). No caching of order or inventory state.
- No distributed locks for correctness. Use DB transactions, unique constraints,
  optimistic versioning, and `SKIP LOCKED`. If a lock is ever added, it needs
  fencing and an ADR.

## Stack
Java 21, Spring Boot MVC, Gradle, gRPC, Kafka (KRaft, single node), Debezium,
Postgres (one container, one DB per service), Redis, Elasticsearch (512 MB heap),
Flyway, JUnit 5, Mockito, Testcontainers, JaCoCo. Planned: Resilience4j,
Prometheus, Grafana, k6.
Don't add libraries without asking.

## Rules
- Constructor injection; no business logic in controllers or consumers.
- Events: topic `<domain>.<event>.v1`, include `eventId`, consumers dedupe on it.
- Money as integer minor units plus currency code, never floating point.
- Every behavior change comes with a test, including one failure-path test.
- Use real Postgres/Kafka/Redis via Testcontainers; don't mock the database.
- Never weaken, skip, or delete a failing test; explain why it fails.
- Never invent library APIs or config keys. Say "verify in the docs" if unsure.
- Ask before changing event schemas, DB schema, or gRPC contracts.
- Avoid `synchronized` around blocking calls (virtual thread pinning); flag
  connection-pool bottlenecks.
- Coverage numbers don't prove correctness. Proofs below do.

## Build order and status
Numbers below are build steps. Update "Proof exists" only when the result is saved
in `docs/results/` with date and setup.

| Step | What                                                              | Built     | Proof exists           | Result file |
|------|-------------------------------------------------------------------|-----------|------------------------|-------------|
| 1    | product-service: catalog CRUD, Redis cache, invalidation          | yes       | <fill in>              |             |
| 2    | order-service + outbox + Debezium + one idempotent consumer       | yes       | <fill in: chaos test?> |             |
| 3    | search-service: ES index fed by product events (replay-safe)      | <fill in> | <fill in>              |             |
| 5    | payment-service + saga + compensation (one test per failure path) | no        | no                     |             |
| 6    | Resilience: gRPC deadlines, circuit breaker, idempotency keys     | no        | no                     |             |
| 7    | Observability + one load test (hardware, tool, bottleneck)        | no        | no                     |             |
| 8    | user-service, JWT, MFA                                            | no        | no                     |             |

Built does not mean done. Code without its proof counts as unfinished.

**Proofs required**
- Step 2: chaos test kills a service or Kafka Connect mid-flow; zero lost, zero duplicate orders.
- Step 3: replaying the same event twice leaves the index unchanged.
- Step 4: exactly 10 of 1,000 concurrent buyers succeed for 10 units.
- Step 5: saga state machine CREATED -> PAYMENT_PENDING -> RESERVED -> CONFIRMED | CANCELLED,
  with compensation (payment failure releases stock) and a timeout job for stuck orders.
- Step 6: slow or kill payment-service; no cascading failure; a retried request doesn't charge twice.

## If time runs short (cut from the bottom)
Minimum credible project = steps 1, 2, 4, 5 plus 4 ADRs. Drop in this order:
user-service/MFA, then observability, then search. Never drop the proof for a step that stays.

## ADRs (docs/adr/)
Written: <fill in>. Still to write: outbox vs dual-write, optimistic vs pessimistic
locking, saga choreography vs orchestration, why gRPC, no Redis locks for correctness,
CQRS split of product and search.

## Resource budget
Whole dev stack within roughly 6 GB. Kafka single node (KRaft). Elasticsearch
`-Xms512m -Xmx512m`. Cap each service JVM (e.g. `-Xmx256m`). Compose profiles:
`core` (daily) and `full` (demos, chaos tests). Prefer Testcontainers for tests.

## How to answer
Short plan first, then code, then "Risks and how to test".
Explain trade-offs. For each design ask: what fails, how do we detect it, how do we
test it, what breaks at 10x load? If I'm wrong, say so. Point out scope creep and
tutorial-pattern choices. Hold me to the proofs: if I ask for a step before the
previous one has its proof, say so.

## Current work (update every session)
- Goal: <e.g. step 4, inventory-service reservation + locking>
- Done so far: product-service, order-service built; coverage reports exist
- Next:
- Stuck on:
- Relevant files / ADRs:

## Goal
Build a backend that shows senior (SDE 3) engineering: owning hard problems,
making trade-offs explicit, proving correctness under failure, and measuring
performance. Depth over breadth. A small system that is provably correct beats
a big one that only works on the happy path.

### What "done" looks like (each needs a proof, not a claim)
1. **Reliable events:** transactional outbox + Debezium + idempotent consumers.
   Proof: chaos test kills a service, Kafka Connect, or a consumer mid-flow;
   asserts zero lost and zero duplicate orders.
2. **Order saga:** explicit state machine (CREATED -> PAYMENT_PENDING -> RESERVED
   -> CONFIRMED | CANCELLED) with compensation and timeouts for stuck orders.
   Proof: one test per failure path.
3. **Safe inventory:** concurrency-controlled reservations with expiry.
   Proof: 1,000 concurrent buyers vs 10 units, exactly 10 succeed.
4. **Resilience:** deadlines on every gRPC call, circuit breaker, bulkhead,
   idempotency keys. Proof: slow or kill payment-service; the system degrades
   without cascading.
5. **Observability + numbers:** tracing across gateway, gRPC, and Kafka;
   metrics for rate, errors, p99, consumer lag; one load test with recorded
   results (hardware, tool, data size, bottleneck found).
6. **Decisions written down:** 4+ short ADRs (outbox vs dual-write, optimistic vs
   pessimistic locking, saga choreography vs orchestration, why gRPC).
   Each states what was rejected and what breaks at scale.
7. **Operable:** `docker compose up` runs the core stack within the memory
   budget; CI runs all tests on every push.

### Targets (measure first, then record real values; never claim unmeasured numbers)
- Zero lost/duplicate orders across all chaos runs.
- Zero oversold units across all concurrency runs.
- Document the p99 latency and throughput at which the system degrades, and
  why (the bottleneck matters more than the number).
- Cache hit rate and consumer lag visible on a dashboard.

### Build order (do not start a step before the previous one has its proof)
1. Product service + search -> 2. Order + outbox + Debezium + one idempotent
   consumer -> 3. Inventory + concurrency test -> 4. Payment + saga + compensation
   -> 5. Resilience + idempotency keys -> 6. Observability + load test
   -> 7. User service, JWT, MFA.

### Non-goals
Frontend, real payments, multi-region, Kubernetes, features beyond the above.

### How the AI should help with this goal
- Hold me to the proofs above. If I ask for a feature before the previous
  step's proof exists, point it out.
- Push back on scope creep and on resume-driven additions.
- For each design, ask: what fails, how do we detect it, how do we test it,
  what breaks at 10x load?
- Challenge my ADR reasoning like a staff engineer in a design review.
- Tell me when something is a tutorial pattern versus a production concern.
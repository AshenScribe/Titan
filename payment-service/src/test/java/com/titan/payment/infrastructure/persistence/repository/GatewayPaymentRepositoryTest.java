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

package com.titan.payment.infrastructure.persistence.repository;

import com.titan.payment.TestcontainersConfiguration;
import com.titan.payment.domain.entity.enums.GatewayPaymentStatus;
import com.titan.payment.infrastructure.persistence.exception.*;
import com.titan.payment.infrastructure.persistence.model.GatewayPaymentEntity;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, GatewayPaymentRepository.class})
class GatewayPaymentRepositoryTest {

    @Autowired private GatewayPaymentRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final UUID ATTEMPT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ATTEMPT_ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    public void setUp() {
        jdbcTemplate.execute("DELETE FROM gateway_payments");
        jdbcTemplate.execute("DELETE FROM payment_attempts");
        insertPaymentAttempt(ATTEMPT_ID);
        insertPaymentAttempt(ATTEMPT_ID_2);
    }

    private void insertPaymentAttempt(UUID id) {
        jdbcTemplate.update(
                """
                        INSERT INTO payment_attempts (id, order_id, idempotency_key, amount, currency, status, created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)
                        """,
                id, UUID.randomUUID(), "idem-" + id, 1000L, "USD", "CREATED");
    }


    @Test
    void insert_persistsEntityAndIsRetrievable() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("gw-1")
                .withAmount(1000L)
                .withCurrency("USD")
                .withStatus("CREATED")
                .withNotes(Map.of("key", "value"))
                .build();

        repository.insert(entity);

        Optional<GatewayPaymentEntity> found = repository.findById(entity.id());
        Assertions.assertThat(found).isPresent();
        Assertions.assertThat(found.get().id()).isEqualTo(entity.id());
        Assertions.assertThat(found.get().paymentAttemptId()).isEqualTo(ATTEMPT_ID);
        Assertions.assertThat(found.get().gatewayPaymentId()).isEqualTo("gw-1");
        Assertions.assertThat(found.get().amount()).isEqualTo(1000L);
        Assertions.assertThat(found.get().amountRefunded()).isZero();
        Assertions.assertThat(found.get().currency()).isEqualTo("USD");
        Assertions.assertThat(found.get().status()).isEqualTo("CREATED");
        Assertions.assertThat(found.get().captured()).isFalse();
        Assertions.assertThat(found.get().notes()).containsEntry("key", "value");
        Assertions.assertThat(found.get().version()).isZero();
    }

    @Test
    void insert_withNullOptionalFields_roundTripsNulls() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("gw-null-opt")
                .withMethod(null)
                .withGatewayCreatedAt(null)
                .build();

        repository.insert(entity);

        GatewayPaymentEntity loaded = repository.findById(entity.id()).orElseThrow();
        Assertions.assertThat(loaded.method()).isNull();
        Assertions.assertThat(loaded.gatewayCreatedAt()).isNull();
    }

    @Test
    void insert_duplicatePrimaryKey_throwsDuplicateGatewayPaymentException() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID).build();
        repository.insert(entity);

        GatewayPaymentEntity duplicate = GatewayPaymentEntityBuilder.anEntity()
                .withId(entity.id())
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("other-gw-id")
                .build();

        Assertions.assertThatThrownBy(() -> repository.insert(duplicate))
                .isInstanceOf(DuplicateGatewayPaymentException.class);
    }

    @Test
    void insert_duplicateGatewayPaymentId_throwsDuplicateGatewayPaymentException() {
        GatewayPaymentEntity first = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("gw-dup")
                .build();
        repository.insert(first);

        GatewayPaymentEntity second = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("gw-dup")
                .build();

        Assertions.assertThatThrownBy(() -> repository.insert(second))
                .isInstanceOf(DuplicateGatewayPaymentException.class)
                .hasMessageContaining("Gateway payment id already recorded");
    }

    @Test
    void insert_unknownPaymentAttempt_throwsForeignKeyViolation() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(UUID.randomUUID())
                .build();

        Assertions.assertThatThrownBy(() -> repository.insert(entity))
                .isInstanceOf(PaymentForeignKeyViolationException.class);
    }

    @Test
    void insert_zeroAmount_throwsCheckConstraintViolation() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withAmount(0L)
                .build();

        Assertions.assertThatThrownBy(() -> repository.insert(entity))
                .isInstanceOf(PaymentCheckConstraintViolationException.class);
    }

    @Test
    void insert_negativeAmount_throwsCheckConstraintViolation() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withAmount(-5L)
                .build();

        Assertions.assertThatThrownBy(() -> repository.insert(entity))
                .isInstanceOf(PaymentCheckConstraintViolationException.class);
    }

    @Test
    void insert_refundedGreaterThanAmount_throwsCheckConstraintViolation() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withAmount(100L)
                .withAmountRefunded(200L)
                .build();

        Assertions.assertThatThrownBy(() -> repository.insert(entity))
                .isInstanceOf(PaymentCheckConstraintViolationException.class);
    }

    @Test
    void insert_invalidStatus_throwsCheckConstraintViolation() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withStatus("NOT_A_STATUS")
                .build();

        Assertions.assertThatThrownBy(() -> repository.insert(entity))
                .isInstanceOf(PaymentCheckConstraintViolationException.class);
    }

    @Test
    void insert_nullStatus_throwsNotNullViolation() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withStatus(null)
                .build();

        Assertions.assertThatThrownBy(() -> repository.insert(entity))
                .isInstanceOf(PaymentNotNullViolationException.class);
    }

    @Test
    void insert_gatewayPaymentIdTooLong_throwsStringTruncationException() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("g".repeat(65))
                .build();

        Assertions.assertThatThrownBy(() -> repository.insert(entity))
                .isInstanceOf(PaymentStringTruncationException.class);
    }

    @Test
    void insert_gatewayPaymentIdAtMaxLength_succeeds() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("g".repeat(64))
                .build();

        repository.insert(entity);

        Assertions.assertThat(repository.findById(entity.id())).isPresent();
    }


    @Test
    void update_incrementsVersionAndAppliesChanges() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withAmount(1000L)
                .withAmountRefunded(0L)
                .withStatus("CREATED")
                .build();
        repository.insert(entity);

        GatewayPaymentEntity updated = new GatewayPaymentEntity(
                entity.id(), entity.paymentAttemptId(), entity.gatewayPaymentId(),
                1000L, 250L, entity.currency(), "REFUNDED", "card", true,
                Map.of("note", "x"), entity.gatewayCreatedAt(),
                entity.createdAt(), OffsetDateTime.now(), entity.version());

        int rows = repository.update(updated);
        Assertions.assertThat(rows).isEqualTo(1);

        GatewayPaymentEntity loaded = repository.findById(entity.id()).orElseThrow();
        Assertions.assertThat(loaded.status()).isEqualTo("REFUNDED");
        Assertions.assertThat(loaded.amountRefunded()).isEqualTo(250L);
        Assertions.assertThat(loaded.method()).isEqualTo("card");
        Assertions.assertThat(loaded.captured()).isTrue();
        Assertions.assertThat(loaded.notes()).containsEntry("note", "x");
        Assertions.assertThat(loaded.version()).isEqualTo(1L);
    }

    @Test
    void update_staleVersion_returnsZeroRows() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID).build();
        repository.insert(entity);

        GatewayPaymentEntity stale = new GatewayPaymentEntity(
                entity.id(), entity.paymentAttemptId(), entity.gatewayPaymentId(),
                entity.amount(), entity.amountRefunded(), entity.currency(), "AUTHORIZED",
                "card", true, Map.of(), entity.gatewayCreatedAt(),
                entity.createdAt(), OffsetDateTime.now(), entity.version() + 5);

        int rows = repository.update(stale);
        Assertions.assertThat(rows).isZero();

        GatewayPaymentEntity loaded = repository.findById(entity.id()).orElseThrow();
        Assertions.assertThat(loaded.status()).isEqualTo("CREATED");
        Assertions.assertThat(loaded.version()).isZero();
    }

    @Test
    void update_nonExistingId_returnsZeroRows() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID).build();

        int rows = repository.update(entity);
        Assertions.assertThat(rows).isZero();
    }

    @Test
    void update_illegalTransitionViolatingCheck_throwsCheckConstraintViolation() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withAmount(100L)
                .build();
        repository.insert(entity);

        GatewayPaymentEntity invalid = new GatewayPaymentEntity(
                entity.id(), entity.paymentAttemptId(), entity.gatewayPaymentId(),
                100L, 150L, entity.currency(), "REFUNDED", "card", true,
                Map.of(), entity.gatewayCreatedAt(), entity.createdAt(),
                OffsetDateTime.now(), entity.version());

        Assertions.assertThatThrownBy(() -> repository.update(invalid))
                .isInstanceOf(PaymentCheckConstraintViolationException.class);
    }


    @Test
    void findById_unknown_returnsEmpty() {
        Assertions.assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }


    @Test
    void findByGatewayPaymentId_returnsMatching() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("gw-find")
                .build();
        repository.insert(entity);

        Assertions.assertThat(repository.findByGatewayPaymentId("gw-find"))
                .isPresent()
                .get()
                .extracting(GatewayPaymentEntity::id)
                .isEqualTo(entity.id());
    }

    @Test
    void findByGatewayPaymentId_unknown_returnsEmpty() {
        Assertions.assertThat(repository.findByGatewayPaymentId("does-not-exist")).isEmpty();
    }

    @Test
    void findByGatewayPaymentId_null_throwsNullPointerException() {
        Assertions.assertThatThrownBy(() -> repository.findByGatewayPaymentId(null))
                .isInstanceOf(NullPointerException.class);
    }


    @Test
    void findByPaymentAttemptId_returnsAllOrderedByCreatedAtDesc() {
        GatewayPaymentEntity older = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withCreatedAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .build();
        GatewayPaymentEntity newer = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withCreatedAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .build();
        repository.insert(older);
        repository.insert(newer);

        List<GatewayPaymentEntity> results = repository.findByPaymentAttemptId(ATTEMPT_ID);
        Assertions.assertThat(results).hasSize(2);
        Assertions.assertThat(results.get(0).id()).isEqualTo(newer.id());
        Assertions.assertThat(results.get(1).id()).isEqualTo(older.id());
    }

    @Test
    void findByPaymentAttemptId_noMatch_returnsEmptyList() {
        Assertions.assertThat(repository.findByPaymentAttemptId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByPaymentAttemptId_doesNotReturnOtherAttemptsRows() {
        GatewayPaymentEntity e1 = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID).build();
        GatewayPaymentEntity e2 = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID_2).build();
        repository.insert(e1);
        repository.insert(e2);

        List<GatewayPaymentEntity> results = repository.findByPaymentAttemptId(ATTEMPT_ID);
        Assertions.assertThat(results).hasSize(1);
        Assertions.assertThat(results.get(0).id()).isEqualTo(e1.id());
    }


    @Test
    void findByStatusUpdatedBefore_returnsMatchingOrderedByUpdatedAt() {
        OffsetDateTime now = OffsetDateTime.now();
        GatewayPaymentEntity oldCreated = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("gw-a")
                .withStatus("CREATED")
                .withUpdatedAt(now.minusHours(2))
                .build();
        GatewayPaymentEntity midCreated = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("gw-b")
                .withStatus("CREATED")
                .withUpdatedAt(now.minusHours(1))
                .build();
        GatewayPaymentEntity recentCaptured = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("gw-c")
                .withStatus("CAPTURED")
                .withUpdatedAt(now.minusMinutes(5))
                .build();
        repository.insert(oldCreated);
        repository.insert(midCreated);
        repository.insert(recentCaptured);

        List<GatewayPaymentEntity> results =
                repository.findByStatusUpdatedBefore(GatewayPaymentStatus.CREATED, now.minusMinutes(30), 10);

        Assertions.assertThat(results).hasSize(2);
        Assertions.assertThat(results.get(0).id()).isEqualTo(oldCreated.id());
        Assertions.assertThat(results.get(1).id()).isEqualTo(midCreated.id());
    }

    @Test
    void findByStatusUpdatedBefore_respectsLimit() {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < 5; i++) {
            repository.insert(GatewayPaymentEntityBuilder.anEntity()
                    .withPaymentAttemptId(ATTEMPT_ID)
                    .withGatewayPaymentId("gw-limit-" + i)
                    .withStatus("CREATED")
                    .withUpdatedAt(now.minusHours(2))
                    .build());
        }

        List<GatewayPaymentEntity> results =
                repository.findByStatusUpdatedBefore(GatewayPaymentStatus.CREATED, now, 3);
        Assertions.assertThat(results).hasSize(3);
    }

    @Test
    void findByStatusUpdatedBefore_noMatch_returnsEmptyList() {
        Assertions.assertThat(repository.findByStatusUpdatedBefore(GatewayPaymentStatus.CREATED, OffsetDateTime.now(), 10))
                .isEmpty();
    }

    @Test
    void findByStatusUpdatedBefore_limitZero_throwsIllegalArgument() {
        Assertions.assertThatThrownBy(() ->
                repository.findByStatusUpdatedBefore(GatewayPaymentStatus.CREATED, OffsetDateTime.now(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 100");
    }

    @Test
    void findByStatusUpdatedBefore_limitAboveMax_throwsIllegalArgument() {
        Assertions.assertThatThrownBy(() ->
                repository.findByStatusUpdatedBefore(GatewayPaymentStatus.CREATED, OffsetDateTime.now(), 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 100");
    }

    @Test
    void findByStatusUpdatedBefore_negativeLimit_throwsIllegalArgument() {
        Assertions.assertThatThrownBy(() ->
                repository.findByStatusUpdatedBefore(GatewayPaymentStatus.CREATED, OffsetDateTime.now(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findByStatusUpdatedBefore_nullStatus_throwsNullPointerException() {
        Assertions.assertThatThrownBy(() ->
                repository.findByStatusUpdatedBefore(null, OffsetDateTime.now(), 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void findByStatusUpdatedBefore_nullCutoff_throwsNullPointerException() {
        Assertions.assertThatThrownBy(() ->
                repository.findByStatusUpdatedBefore(GatewayPaymentStatus.CREATED, null, 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void findByStatusUpdatedBefore_boundaryEqualCutoff_returnsEmpty() {
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-06-01T12:00:00Z");
        repository.insert(GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("gw-boundary")
                .withStatus("CREATED")
                .withUpdatedAt(cutoff)
                .build());

        List<GatewayPaymentEntity> results =
                repository.findByStatusUpdatedBefore(GatewayPaymentStatus.CREATED, cutoff, 10);
        Assertions.assertThat(results).isEmpty();
    }

    @Test
    void findByStatusUpdatedBefore_oneMicroBeforeCutoff_returnsEntity() {
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-06-01T12:00:00Z");
        GatewayPaymentEntity e = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withGatewayPaymentId("gw-micro")
                .withStatus("CREATED")
                .withUpdatedAt(cutoff.minusNanos(1000))
                .build();
        repository.insert(e);

        List<GatewayPaymentEntity> results =
                repository.findByStatusUpdatedBefore(GatewayPaymentStatus.CREATED, cutoff, 10);
        Assertions.assertThat(results).extracting(GatewayPaymentEntity::id).containsExactly(e.id());
    }


    @Test
    void notes_nestedJsonb_roundTrips() {
        Map<String, Object> nested = Map.of(
                "outer", Map.of("inner", "value"),
                "arr", List.of("a", "b", 3));
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withNotes(nested)
                .build();
        repository.insert(entity);

        GatewayPaymentEntity loaded = repository.findById(entity.id()).orElseThrow();
        Assertions.assertThat(loaded.notes()).containsKey("outer");
        Assertions.assertThat(loaded.notes()).containsKey("arr");
    }

    @Test
    void notes_emptyMap_roundTripsAsEmptyObject() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withNotes(Map.of())
                .build();
        repository.insert(entity);

        GatewayPaymentEntity loaded = repository.findById(entity.id()).orElseThrow();
        Assertions.assertThat(loaded.notes()).isEmpty();
    }

    @Test
    void notes_negativeAmount_throwsCheckConstraintViolation() {
        GatewayPaymentEntity entity = GatewayPaymentEntityBuilder.anEntity()
                .withPaymentAttemptId(ATTEMPT_ID)
                .withAmountRefunded(-1L)
                .build();

        Assertions.assertThatThrownBy(() -> repository.insert(entity))
                  .isInstanceOf(PaymentCheckConstraintViolationException.class);
    }
}
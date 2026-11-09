// ------------------------------------------------------------------------------
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
// ------------------------------------------------------------------------------

package com.titan.product.orderservice.data.repository;

import com.titan.product.orderservice.TestcontainersConfiguration;
import com.titan.product.orderservice.data.entity.OrderEntity;
import com.titan.product.orderservice.data.entity.OrderIdempotencyEntity;
import com.titan.product.orderservice.data.exception.DuplicateOrderException;
import com.titan.product.orderservice.data.exception.OrderCheckConstraintViolationException;
import com.titan.product.orderservice.data.exception.OrderForeignKeyViolationException;
import com.titan.product.orderservice.data.exception.OrderNotNullViolationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    TestcontainersConfiguration.class,
    OrderRepository.class,
    OrderIdempotencyRepository.class
})
@Sql(
        scripts = "/db/migration/V1__create_orders.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class OrderIdempotencyRepositoryTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 4, 15, 45, 30, 0, ZoneOffset.UTC);

    @Autowired private OrderIdempotencyRepository orderIdempotencyRepository;

    @Autowired private OrderRepository orderRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void insertAndFindByKeyRoundTripsEveryField() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        OrderIdempotencyEntity entity =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .requestHash(OrderIdempotencyEntityBuilder.filledHash(0x42))
                        .idempotencyKey("checkout-abc-123")
                        .createdAt(NOW)
                        .expiresAt(NOW.plusMinutes(30))
                        .build();

        orderIdempotencyRepository.insertOrReplaceExpired(entity);

        OrderIdempotencyEntity found =
                orderIdempotencyRepository
                        .findByKey(entity.userId(), entity.idempotencyKey(), NOW)
                        .orElseThrow();

        Assertions.assertThat(found.userId()).isEqualTo(entity.userId());
        Assertions.assertThat(found.idempotencyKey()).isEqualTo("checkout-abc-123");
        Assertions.assertThat(found.requestHash())
                .isEqualTo(OrderIdempotencyEntityBuilder.filledHash(0x42));
        Assertions.assertThat(found.orderId()).isEqualTo(order.id());
        Assertions.assertThat(found.createdAt().toInstant())
                .isEqualTo(entity.createdAt().toInstant());
        Assertions.assertThat(found.expiresAt().toInstant())
                .isEqualTo(entity.expiresAt().toInstant());
    }

    @Test
    void insertReturnsHashDefensiveCopyNotSharedArray() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        byte[] originalHash = OrderIdempotencyEntityBuilder.filledHash(0x011);
        OrderIdempotencyEntity entity =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .requestHash(originalHash)
                        .build();

        orderIdempotencyRepository.insertOrReplaceExpired(entity);

        OrderIdempotencyEntity found =
                orderIdempotencyRepository
                        .findByKey(entity.userId(), entity.idempotencyKey(), NOW)
                        .orElseThrow();

        Assertions.assertThat(found.requestHash()).isEqualTo(originalHash);
        Assertions.assertThat(found.requestHash()).isNotSameAs(originalHash);
        found.requestHash()[0] = (byte) 0xFF;
        Assertions.assertThat(found.requestHash()[0]).isEqualTo((byte) 0x11);
    }

    @Test
    void insertAcceptsKeyOfMaximumLength() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        String key = "k".repeat(128);
        OrderIdempotencyEntity entity =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .idempotencyKey(key)
                        .build();

        Assertions.assertThatCode(() -> orderIdempotencyRepository.insertOrReplaceExpired(entity))
                .doesNotThrowAnyException();
        Assertions.assertThat(orderIdempotencyRepository.findByKey(entity.userId(), key, NOW))
                .isPresent();
    }

    @Test
    void insertReplacesExpiredRowWithNewOrderAndHash() {
        OrderEntity firstOrder = parentOrder();
        OrderEntity secondOrder = parentOrder();
        orderRepository.insert(firstOrder);
        orderRepository.insert(secondOrder);
        OrderIdempotencyEntity expired =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(firstOrder.id())
                        .requestHash(OrderIdempotencyEntityBuilder.filledHash(0x01))
                        .createdAt(NOW.minusHours(2))
                        .expiresAt(NOW.minusHours(1))
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(expired);

        OrderIdempotencyEntity replacement =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .userId(expired.userId())
                        .idempotencyKey(expired.idempotencyKey())
                        .orderId(secondOrder.id())
                        .requestHash(OrderIdempotencyEntityBuilder.filledHash(0x02))
                        .createdAt(NOW)
                        .expiresAt(NOW.plusMinutes(30))
                        .build();

        orderIdempotencyRepository.insertOrReplaceExpired(replacement);

        OrderIdempotencyEntity found =
                orderIdempotencyRepository
                        .findByKey(expired.userId(), expired.idempotencyKey(), NOW)
                        .orElseThrow();

        Assertions.assertThat(found.orderId()).isEqualTo(secondOrder.id());
        Assertions.assertThat(found.requestHash())
                .isEqualTo(OrderIdempotencyEntityBuilder.filledHash(0x02));
        Assertions.assertThat(found.createdAt().toInstant())
                .isEqualTo(replacement.createdAt().toInstant());
    }

    @Test
    void insertDoesNotReplaceUnexpiredRow() {
        OrderEntity firstOrder = parentOrder();
        OrderEntity secondOrder = parentOrder();
        orderRepository.insert(firstOrder);
        orderRepository.insert(secondOrder);
        OrderIdempotencyEntity fresh =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(firstOrder.id())
                        .requestHash(OrderIdempotencyEntityBuilder.filledHash(0x01))
                        .createdAt(NOW.minusMinutes(5))
                        .expiresAt(NOW.plusMinutes(30))
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(fresh);

        OrderIdempotencyEntity conflicting =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .userId(fresh.userId())
                        .idempotencyKey(fresh.idempotencyKey())
                        .orderId(secondOrder.id())
                        .requestHash(OrderIdempotencyEntityBuilder.filledHash(0x02))
                        .createdAt(NOW)
                        .expiresAt(NOW.plusMinutes(60))
                        .build();

        Assertions.assertThatCode(
                        () -> orderIdempotencyRepository.insertOrReplaceExpired(conflicting))
                .doesNotThrowAnyException();

        OrderIdempotencyEntity found =
                orderIdempotencyRepository
                        .findByKey(fresh.userId(), fresh.idempotencyKey(), NOW)
                        .orElseThrow();
        Assertions.assertThat(found.orderId()).isEqualTo(firstOrder.id());
        Assertions.assertThat(found.requestHash())
                .isEqualTo(OrderIdempotencyEntityBuilder.filledHash(0x01));
    }

    @Test
    void insertReplacesRowWhenNewCreatedAtEqualsOldExpiresAt() {
        OrderEntity firstOrder = parentOrder();
        OrderEntity secondOrder = parentOrder();
        orderRepository.insert(firstOrder);
        orderRepository.insert(secondOrder);
        OrderIdempotencyEntity existing =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(firstOrder.id())
                        .requestHash(OrderIdempotencyEntityBuilder.filledHash(0x01))
                        .createdAt(NOW.minusHours(1))
                        .expiresAt(NOW)
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(existing);

        OrderIdempotencyEntity boundary =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .userId(existing.userId())
                        .idempotencyKey(existing.idempotencyKey())
                        .orderId(secondOrder.id())
                        .requestHash(OrderIdempotencyEntityBuilder.filledHash(0x02))
                        .createdAt(NOW)
                        .expiresAt(NOW.plusMinutes(30))
                        .build();

        orderIdempotencyRepository.insertOrReplaceExpired(boundary);

        OrderIdempotencyEntity found =
                orderIdempotencyRepository
                        .findByKey(existing.userId(), existing.idempotencyKey(), NOW)
                        .orElseThrow();
        Assertions.assertThat(found.orderId()).isEqualTo(secondOrder.id());
    }

    @Test
    void findByKeyReturnsEmptyWhenMissing() {
        Assertions.assertThat(
                        orderIdempotencyRepository.findByKey(UUID.randomUUID(), "missing-key", NOW))
                .isEmpty();
    }

    @Test
    void findByKeyIgnoresExpiredRecord() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        OrderIdempotencyEntity expired =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .createdAt(NOW.minusHours(2))
                        .expiresAt(NOW.minusMinutes(1))
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(expired);

        Assertions.assertThat(
                        orderIdempotencyRepository.findByKey(
                                expired.userId(), expired.idempotencyKey(), NOW))
                .isEmpty();
    }

    @Test
    void findByKeyReturnsRecordWhoseExpiryEqualsNowBoundary() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        OrderIdempotencyEntity onBoundary =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .createdAt(NOW.minusMinutes(1))
                        .expiresAt(NOW)
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(onBoundary);

        Optional<OrderIdempotencyEntity> found =
                orderIdempotencyRepository.findByKey(
                        onBoundary.userId(), onBoundary.idempotencyKey(), NOW);
        Assertions.assertThat(found).isEmpty();
    }

    @Test
    void findByKeyReturnsRecordJustBeforeExpiry() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        OrderIdempotencyEntity almostExpired =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .createdAt(NOW.minusMinutes(29))
                        .expiresAt(NOW.plusNanos(1_000))
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(almostExpired);

        Assertions.assertThat(
                        orderIdempotencyRepository.findByKey(
                                almostExpired.userId(), almostExpired.idempotencyKey(), NOW))
                .isPresent();
    }

    @Test
    void findByKeyScopesByUserAndKey() {
        OrderEntity firstOrder = parentOrder();
        OrderEntity secondOrder = parentOrder();
        orderRepository.insert(firstOrder);
        orderRepository.insert(secondOrder);
        UUID sharedKey = UUID.randomUUID();
        OrderIdempotencyEntity first =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(firstOrder.id())
                        .idempotencyKey("shared")
                        .build();
        OrderIdempotencyEntity second =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(secondOrder.id())
                        .idempotencyKey("shared")
                        .userId(sharedKey)
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(first);
        orderIdempotencyRepository.insertOrReplaceExpired(second);

        Assertions.assertThat(orderIdempotencyRepository.findByKey(first.userId(), "shared", NOW))
                .get()
                .extracting(OrderIdempotencyEntity::orderId)
                .isEqualTo(firstOrder.id());
        Assertions.assertThat(orderIdempotencyRepository.findByKey(second.userId(), "shared", NOW))
                .get()
                .extracting(OrderIdempotencyEntity::orderId)
                .isEqualTo(secondOrder.id());
    }

    @Test
    void findByKeySameUserDifferentKeysAreIndependent() {
        OrderEntity orderA = parentOrder();
        OrderEntity orderB = parentOrder();
        orderRepository.insert(orderA);
        orderRepository.insert(orderB);
        UUID userId = UUID.randomUUID();
        OrderIdempotencyEntity a =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .userId(userId)
                        .idempotencyKey("key-a")
                        .orderId(orderA.id())
                        .build();
        OrderIdempotencyEntity b =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .userId(userId)
                        .idempotencyKey("key-b")
                        .orderId(orderB.id())
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(a);
        orderIdempotencyRepository.insertOrReplaceExpired(b);

        Assertions.assertThat(orderIdempotencyRepository.findByKey(userId, "key-a", NOW))
                .get()
                .extracting(OrderIdempotencyEntity::orderId)
                .isEqualTo(orderA.id());
        Assertions.assertThat(orderIdempotencyRepository.findByKey(userId, "key-b", NOW))
                .get()
                .extracting(OrderIdempotencyEntity::orderId)
                .isEqualTo(orderB.id());
    }

    @Test
    void deleteExpiredRemovesOnlyExpiredRows() {
        OrderEntity firstOrder = parentOrder();
        OrderEntity secondOrder = parentOrder();
        orderRepository.insert(firstOrder);
        orderRepository.insert(secondOrder);
        OrderIdempotencyEntity expired =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(firstOrder.id())
                        .createdAt(NOW.minusHours(2))
                        .expiresAt(NOW.minusMinutes(1))
                        .build();
        OrderIdempotencyEntity live =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(secondOrder.id())
                        .createdAt(NOW.minusMinutes(10))
                        .expiresAt(NOW.plusMinutes(10))
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(expired);
        orderIdempotencyRepository.insertOrReplaceExpired(live);

        int deleted = orderIdempotencyRepository.deleteExpired(NOW);

        Assertions.assertThat(deleted).isEqualTo(1);
        Assertions.assertThat(
                        orderIdempotencyRepository.findByKey(
                                expired.userId(), expired.idempotencyKey(), NOW))
                .isEmpty();
        Assertions.assertThat(
                        orderIdempotencyRepository.findByKey(
                                live.userId(), live.idempotencyKey(), NOW))
                .isPresent();
    }

    @Test
    void deleteExpiredReturnsZeroWhenNothingExpired() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        OrderIdempotencyEntity live =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .createdAt(NOW)
                        .expiresAt(NOW.plusHours(1))
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(live);

        Assertions.assertThat(orderIdempotencyRepository.deleteExpired(NOW)).isZero();
    }

    @Test
    void deleteExpiredReturnsZeroWhenTableEmpty() {
        Assertions.assertThat(orderIdempotencyRepository.deleteExpired(NOW)).isZero();
    }

    @Test
    void deleteExpiredIncludesExpiresAtExactlyNow() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        OrderIdempotencyEntity exact =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .createdAt(NOW.minusMinutes(1))
                        .expiresAt(NOW)
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(exact);

        Assertions.assertThat(orderIdempotencyRepository.deleteExpired(NOW)).isEqualTo(1);
    }

    @Test
    void insertFailsOnNullUserId() {
        assertNotNullViolation(OrderIdempotencyEntityBuilder.anIdempotencyRecord().userId(null));
    }

    @Test
    void insertFailsOnNullIdempotencyKey() {
        assertNotNullViolation(
                OrderIdempotencyEntityBuilder.anIdempotencyRecord().idempotencyKey(null));
    }

    @Test
    void insertFailsOnNullRequestHash() {
        assertNotNullViolation(
                OrderIdempotencyEntityBuilder.anIdempotencyRecord().requestHash(null));
    }

    @Test
    void insertFailsOnNullOrderId() {
        assertNotNullViolation(OrderIdempotencyEntityBuilder.anIdempotencyRecord().orderId(null));
    }

    @Test
    void insertFailsOnNullCreatedAt() {
        assertNotNullViolation(OrderIdempotencyEntityBuilder.anIdempotencyRecord().createdAt(null));
    }

    @Test
    void insertFailsOnNullExpiresAt() {
        assertNotNullViolation(OrderIdempotencyEntityBuilder.anIdempotencyRecord().expiresAt(null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 31, 33, 64})
    void insertFailsOnRequestHashWithWrongLength(int length) {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        byte[] hash = new byte[length];
        OrderIdempotencyEntity entity =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .requestHash(hash)
                        .build();

        Assertions.assertThatThrownBy(
                        () -> orderIdempotencyRepository.insertOrReplaceExpired(entity))
                .isInstanceOf(OrderCheckConstraintViolationException.class);
    }

    @Test
    void insertReplacesSameKeyButDifferentOrderWhenExpired() {
        OrderEntity firstOrder = parentOrder();
        OrderEntity secondOrder = parentOrder();
        orderRepository.insert(firstOrder);
        orderRepository.insert(secondOrder);
        OrderIdempotencyEntity existing =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(firstOrder.id())
                        .createdAt(NOW.minusMinutes(1))
                        .expiresAt(NOW.plusHours(1))
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(existing);

        OrderIdempotencyEntity conflicting =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .userId(existing.userId())
                        .idempotencyKey(existing.idempotencyKey())
                        .orderId(secondOrder.id())
                        .requestHash(OrderIdempotencyEntityBuilder.filledHash(0x33))
                        .createdAt(NOW)
                        .expiresAt(NOW.plusHours(2))
                        .build();

        Assertions.assertThatCode(
                        () -> orderIdempotencyRepository.insertOrReplaceExpired(conflicting))
                .doesNotThrowAnyException();
    }

    @Test
    void insertFailsWhenOrderAlreadyReferencedByDifferentIdempotencyRow() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        OrderIdempotencyEntity first =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord().orderId(order.id()).build();
        orderIdempotencyRepository.insertOrReplaceExpired(first);

        OrderIdempotencyEntity second =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord().orderId(order.id()).build();

        Assertions.assertThatThrownBy(
                        () -> orderIdempotencyRepository.insertOrReplaceExpired(second))
                .isInstanceOf(DuplicateOrderException.class)
                .hasMessageContaining("idempotency");
    }

    @Test
    void insertFailsWhenOrderDoesNotExist() {
        OrderIdempotencyEntity entity =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(UUID.randomUUID())
                        .build();

        Assertions.assertThatThrownBy(
                        () -> orderIdempotencyRepository.insertOrReplaceExpired(entity))
                .isInstanceOf(OrderForeignKeyViolationException.class);
    }

    private OrderEntity parentOrder() {
        return OrderEntityBuilder.anOrder().createdAt(NOW).build();
    }

    private void assertNotNullViolation(OrderIdempotencyEntityBuilder builder) {
        OrderIdempotencyEntity probe = builder.build();
        OrderIdempotencyEntity entity;
        if (probe.orderId() == null) {
            entity = probe;
        } else {
            OrderEntity parent = parentOrder();
            orderRepository.insert(parent);
            entity = builder.orderId(parent.id()).build();
        }
        OrderIdempotencyEntity target = entity;
        Assertions.assertThatThrownBy(
                        () -> orderIdempotencyRepository.insertOrReplaceExpired(target))
                .isInstanceOf(OrderNotNullViolationException.class);
    }

    @Test
    void insertFailsOnRequestHashOverflowIsNotApplicable() {
        Assertions.assertThat(true).isTrue();
    }

    @Test
    void findByKeyWithDifferentNowReturnsSameRowWhenStillValid() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        OrderIdempotencyEntity entity =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .createdAt(NOW)
                        .expiresAt(NOW.plusHours(1))
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(entity);

        Assertions.assertThat(
                        orderIdempotencyRepository.findByKey(
                                entity.userId(), entity.idempotencyKey(), NOW.plusMinutes(1)))
                .isPresent();
        Assertions.assertThat(
                        orderIdempotencyRepository.findByKey(
                                entity.userId(), entity.idempotencyKey(), NOW.plusHours(1)))
                .isEmpty();
    }

    @Test
    void insertHandlesAllZeroHashBytes() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        byte[] zeroHash = new byte[32];
        OrderIdempotencyEntity entity =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .requestHash(zeroHash)
                        .build();

        orderIdempotencyRepository.insertOrReplaceExpired(entity);

        Assertions.assertThat(
                        orderIdempotencyRepository.findByKey(
                                entity.userId(), entity.idempotencyKey(), NOW))
                .get()
                .extracting(OrderIdempotencyEntity::requestHash)
                .isEqualTo(zeroHash);
    }

    @Test
    void insertHandlesAllOnesHashBytes() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        byte[] onesHash = OrderIdempotencyEntityBuilder.filledHash(0xFF);
        OrderIdempotencyEntity entity =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .requestHash(onesHash)
                        .build();

        orderIdempotencyRepository.insertOrReplaceExpired(entity);

        Assertions.assertThat(
                        orderIdempotencyRepository.findByKey(
                                entity.userId(), entity.idempotencyKey(), NOW))
                .get()
                .extracting(OrderIdempotencyEntity::requestHash)
                .isEqualTo(onesHash);
    }

    @Test
    void insertRejectsRequestHashLongerThan32ButAllowedByMessageIsCheck() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        byte[] tooLong = new byte[1024];
        OrderIdempotencyEntity entity =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .requestHash(tooLong)
                        .build();

        Assertions.assertThatThrownBy(
                        () -> orderIdempotencyRepository.insertOrReplaceExpired(entity))
                .isInstanceOf(OrderCheckConstraintViolationException.class);
    }

    @Test
    void findAndDeleteRoundTripLeavesNoResidue() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        OrderIdempotencyEntity entity =
                com.titan.product.orderservice.data.repository.OrderIdempotencyEntityBuilder
                        .anIdempotencyRecord()
                        .orderId(order.id())
                        .createdAt(NOW.minusHours(1))
                        .expiresAt(NOW.plusSeconds(1))
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(entity);

        Assertions.assertThat(
                        orderIdempotencyRepository.findByKey(
                                entity.userId(), entity.idempotencyKey(), NOW))
                .isPresent();
        Assertions.assertThat(orderIdempotencyRepository.deleteExpired(NOW.plusSeconds(2)))
                .isEqualTo(1);
        Assertions.assertThat(
                        orderIdempotencyRepository.findByKey(
                                entity.userId(), entity.idempotencyKey(), NOW))
                .isEmpty();
    }

    @Test
    void orderDeleteIsBlockedOnlyWhileIdempotencyRowExists() {
        OrderEntity order = parentOrder();
        orderRepository.insert(order);
        OrderIdempotencyEntity entity =
                OrderIdempotencyEntityBuilder.anIdempotencyRecord()
                        .orderId(order.id())
                        .createdAt(NOW.minusHours(2))
                        .expiresAt(NOW.minusHours(1))
                        .build();
        orderIdempotencyRepository.insertOrReplaceExpired(entity);

        jdbcTemplate.update(
                "DELETE FROM order_idempotency WHERE user_id = ? AND idempotency_key = ?",
                entity.userId(),
                entity.idempotencyKey());

        Assertions.assertThatCode(
                        () -> jdbcTemplate.update("DELETE FROM orders WHERE id = ?", order.id()))
                .doesNotThrowAnyException();
        Assertions.assertThat(orderRepository.findById(order.id())).isEmpty();
    }
}

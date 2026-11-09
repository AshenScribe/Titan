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

package com.titan.product.orderservice.domain.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.titan.product.orderservice.TestcontainersConfiguration;
import com.titan.product.orderservice.data.entity.OrderEntity;
import com.titan.product.orderservice.data.entity.OrderItemEntity;
import com.titan.product.orderservice.data.entity.OrderStatus;
import com.titan.product.orderservice.data.repository.OrderItemRepository;
import com.titan.product.orderservice.data.repository.OrderRepository;
import com.titan.product.orderservice.domain.exception.ConflictException;
import com.titan.product.orderservice.domain.exception.DependencyUnavailableException;
import com.titan.product.orderservice.domain.exception.NotFoundException;
import com.titan.product.orderservice.dto.OrderApiModels.CancelOrderRequest;
import com.titan.product.orderservice.dto.OrderApiModels.CreateOrderRequest;
import com.titan.product.orderservice.dto.OrderApiModels.ItemRequest;
import com.titan.product.orderservice.dto.OrderApiModels.OrderResponse;
import com.titan.product.orderservice.dto.OrderApiModels.OrderSummary;
import com.titan.product.orderservice.dto.OrderApiModels.PageResponse;
import com.titan.product.orderservice.service.ProductVariantProvider;
import com.titan.product.orderservice.service.ProductVariantProvider.ProductVariant;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Import({TestcontainersConfiguration.class, OrderServiceIntegrationTest.StubConfig.class})
@Sql(
        scripts = "/db/migration/V1__create_orders.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderServiceIntegrationTest {

    @TestConfiguration
    static class StubConfig {
        @Bean
        ProductVariantProvider productVariantProvider() {
            return mock(ProductVariantProvider.class);
        }
    }

    @Autowired private OrderService service;
    @Autowired private OrderRepository orders;
    @Autowired private OrderItemRepository items;
    @MockitoBean private ProductVariantProvider variants;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void createPersistsOrderAndAllItemsAtomically() {
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        when(variants.getActiveVariant(v1)).thenReturn(variant(v1, "USD", "10.00"));
        when(variants.getActiveVariant(v2)).thenReturn(variant(v2, "USD", "2.50"));

        OrderResponse response =
                service.create(
                        UUID.randomUUID(),
                        new CreateOrderRequest(
                                List.of(new ItemRequest(v1, 2), new ItemRequest(v2, 4)),
                                Map.of("line1", "x"),
                                null,
                                null));

        Assertions.assertThat(orders.findById(response.id())).isPresent();
        List<OrderItemEntity> persisted = items.findByOrderId(response.id());
        Assertions.assertThat(persisted).hasSize(2);
        Assertions.assertThat(persisted)
                .extracting(OrderItemEntity::variantId)
                .containsExactly(v1, v2);
    }

    @Test
    void createRollsBackOrderWhenItemInsertFails() {
        UUID v = UUID.randomUUID();
        when(variants.getActiveVariant(v)).thenReturn(variant(v, "USD", "-1.00"));
        Assertions.assertThatThrownBy(
                        () ->
                                service.create(
                                        UUID.randomUUID(),
                                        new CreateOrderRequest(
                                                List.of(new ItemRequest(v, 1)),
                                                Map.of("line1", "x"),
                                                null,
                                                null)))
                .isInstanceOf(
                        com.titan.product.orderservice.data.exception
                                .OrderCheckConstraintViolationException.class);

        Assertions.assertThat(
                        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isZero();
        Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM order_items", Integer.class))
                .isZero();
    }

    @Test
    void createRollsBackWhenSecondItemInsertFails() {
        UUID ok = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        when(variants.getActiveVariant(ok)).thenReturn(variant(ok, "USD", "10.00"));
        when(variants.getActiveVariant(bad)).thenReturn(variant(bad, "USD", "-1.00"));

        Assertions.assertThatThrownBy(
                        () ->
                                service.create(
                                        UUID.randomUUID(),
                                        new CreateOrderRequest(
                                                List.of(
                                                        new ItemRequest(ok, 1),
                                                        new ItemRequest(bad, 1)),
                                                Map.of("line1", "x"),
                                                null,
                                                null)))
                .isInstanceOf(
                        com.titan.product.orderservice.data.exception
                                .OrderCheckConstraintViolationException.class);

        Assertions.assertThat(
                        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isZero();
        Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM order_items", Integer.class))
                .isZero();
    }

    @Test
    void createDoesNotPersistWhenVariantLookupFails() {
        UUID ok = UUID.randomUUID();
        UUID failing = UUID.randomUUID();
        when(variants.getActiveVariant(ok)).thenReturn(variant(ok, "USD", "10.00"));
        when(variants.getActiveVariant(failing))
                .thenThrow(new DependencyUnavailableException("down"));

        Assertions.assertThatThrownBy(
                        () ->
                                service.create(
                                        UUID.randomUUID(),
                                        new CreateOrderRequest(
                                                List.of(
                                                        new ItemRequest(ok, 1),
                                                        new ItemRequest(failing, 1)),
                                                Map.of("line1", "x"),
                                                null,
                                                null)))
                .isInstanceOf(DependencyUnavailableException.class);

        Assertions.assertThat(
                        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isZero();
    }

    @Test
    void createPersistsCorrectItemOrdering() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(variants.getActiveVariant(a)).thenReturn(variant(a, "USD", "1.00"));
        when(variants.getActiveVariant(b)).thenReturn(variant(b, "USD", "1.00"));
        when(variants.getActiveVariant(c)).thenReturn(variant(c, "USD", "1.00"));

        OrderResponse response =
                service.create(
                        UUID.randomUUID(),
                        new CreateOrderRequest(
                                List.of(
                                        new ItemRequest(a, 1),
                                        new ItemRequest(b, 1),
                                        new ItemRequest(c, 1)),
                                Map.of("line1", "x"),
                                null,
                                null));

        Assertions.assertThat(response.items())
                .extracting(i -> i.variantId())
                .containsExactly(a, b, c);
    }

    @Test
    void getReturnsPersistedOrderAndItems() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, userId, OrderStatus.PAID);
        insertItem(orderId, (short) 0, "p-0");
        insertItem(orderId, (short) 1, "p-1");

        OrderResponse response = service.get(userId, orderId);

        Assertions.assertThat(response.id()).isEqualTo(orderId);
        Assertions.assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        Assertions.assertThat(response.items()).hasSize(2);
        Assertions.assertThat(response.items())
                .extracting(i -> i.productName())
                .containsExactly("p-0", "p-1");
    }

    @Test
    void listReturnsCorrectItemCounts() {
        UUID userId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        insertOrder(a, userId, OrderStatus.PENDING);
        insertOrder(b, userId, OrderStatus.PAID);
        insertOrder(c, userId, OrderStatus.SHIPPED);
        insertItem(a, (short) 0, "a0");
        insertItem(a, (short) 1, "a1");
        insertItem(a, (short) 2, "a2");
        insertItem(b, (short) 0, "b0");
        PageResponse<OrderSummary> page = service.list(userId, 0, 10);

        Assertions.assertThat(page.content()).hasSize(3);
        Assertions.assertThat(page.content())
                .extracting(OrderSummary::id)
                .containsExactlyInAnyOrder(a, b, c);
        Map<UUID, Integer> counts =
                page.content().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        OrderSummary::id, OrderSummary::itemCount));
        Assertions.assertThat(counts.get(a)).isEqualTo(3);
        Assertions.assertThat(counts.get(b)).isEqualTo(1);
        Assertions.assertThat(counts.get(c)).isZero();
    }

    @Test
    void listOnlyReturnsCurrentUsersOrders() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        insertOrder(mine, me, OrderStatus.PENDING);
        insertOrder(theirs, other, OrderStatus.PENDING);

        PageResponse<OrderSummary> page = service.list(me, 0, 10);

        Assertions.assertThat(page.content()).extracting(OrderSummary::id).containsExactly(mine);
        Assertions.assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void listReturnsEmptyPageWhenBeyondLastPage() {
        UUID userId = UUID.randomUUID();
        insertOrder(UUID.randomUUID(), userId, OrderStatus.PENDING);

        PageResponse<OrderSummary> page = service.list(userId, 5, 10);

        Assertions.assertThat(page.content()).isEmpty();
        Assertions.assertThat(page.totalElements()).isEqualTo(1);
        Assertions.assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void listHandlesLastPartialPage() {
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            insertOrder(UUID.randomUUID(), userId, OrderStatus.PENDING);
        }

        PageResponse<OrderSummary> page = service.list(userId, 2, 2);

        Assertions.assertThat(page.content()).hasSize(1);
        Assertions.assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void cancelPersistsStatusAndTimestamp() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, userId, OrderStatus.PENDING);

        service.cancel(userId, orderId, new CancelOrderRequest("changed mind"));

        OrderEntity reloaded = orders.findById(orderId).orElseThrow();
        Assertions.assertThat(reloaded.status()).isEqualTo(OrderStatus.CANCELLED);
        Assertions.assertThat(reloaded.cancelledAt()).isNotNull();
        Assertions.assertThat(reloaded.version()).isEqualTo(1L);
    }

    @Test
    void cancelDoesNotModifyNonPendingOrder() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, userId, OrderStatus.SHIPPED);
        OrderEntity before = orders.findById(orderId).orElseThrow();

        Assertions.assertThatThrownBy(
                        () -> service.cancel(userId, orderId, new CancelOrderRequest("x")))
                .isInstanceOf(ConflictException.class);

        OrderEntity after = orders.findById(orderId).orElseThrow();
        Assertions.assertThat(after.status()).isEqualTo(OrderStatus.SHIPPED);
        Assertions.assertThat(after.cancelledAt()).isNull();
        Assertions.assertThat(after.version()).isEqualTo(before.version());
    }

    @Test
    void cancelReturnsUpdatedOrderState() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, userId, OrderStatus.PENDING);

        OrderResponse response = service.cancel(userId, orderId, new CancelOrderRequest("x"));

        Assertions.assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        Assertions.assertThat(response.cancelledAt()).isNotNull();
    }

    @Test
    void ordersAreIsolatedBetweenUsers() {
        UUID owner = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, owner, OrderStatus.PENDING);

        Assertions.assertThatThrownBy(() -> service.get(UUID.randomUUID(), orderId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelCannotModifyAnotherUsersOrder() {
        UUID owner = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, owner, OrderStatus.PENDING);

        Assertions.assertThatThrownBy(
                        () ->
                                service.cancel(
                                        UUID.randomUUID(), orderId, new CancelOrderRequest("x")))
                .isInstanceOf(NotFoundException.class);

        Assertions.assertThat(orders.findById(orderId).orElseThrow().status())
                .isEqualTo(OrderStatus.PENDING);
    }

    private static ProductVariant variant(UUID id, String currency, String price) {
        return new ProductVariant(
                id,
                UUID.randomUUID(),
                "Product",
                "Variant",
                "SKU",
                new BigDecimal(price),
                currency,
                "{}");
    }

    private void insertOrder(UUID orderId, UUID userId, OrderStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        orders.insert(
                new OrderEntity(
                        orderId,
                        userId,
                        status,
                        "USD",
                        new BigDecimal("10.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("50.00"),
                        BigDecimal.ZERO,
                        null,
                        "{\"line1\":\"x\"}",
                        null,
                        null,
                        0L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        now,
                        now));
    }

    private void insertItem(UUID orderId, short position, String productName) {
        items.insert(
                new OrderItemEntity(
                        UUID.randomUUID(),
                        orderId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        productName,
                        "v",
                        "SKU-" + position,
                        "{}",
                        1,
                        new BigDecimal("10.00"),
                        position,
                        OffsetDateTime.now()));
    }
}

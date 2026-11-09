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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.titan.product.orderservice.data.entity.OrderEntity;
import com.titan.product.orderservice.data.entity.OrderItemEntity;
import com.titan.product.orderservice.data.entity.OrderStatus;
import com.titan.product.orderservice.data.exception.OrderCheckConstraintViolationException;
import com.titan.product.orderservice.data.repository.OrderItemRepository;
import com.titan.product.orderservice.data.repository.OrderRepository;
import com.titan.product.orderservice.domain.exception.ConflictException;
import com.titan.product.orderservice.domain.exception.DependencyUnavailableException;
import com.titan.product.orderservice.domain.exception.NotFoundException;
import com.titan.product.orderservice.domain.exception.OrderApiException;
import com.titan.product.orderservice.dto.OrderApiModels.CancelOrderRequest;
import com.titan.product.orderservice.dto.OrderApiModels.CreateOrderRequest;
import com.titan.product.orderservice.dto.OrderApiModels.ItemRequest;
import com.titan.product.orderservice.dto.OrderApiModels.ItemResponse;
import com.titan.product.orderservice.dto.OrderApiModels.OrderResponse;
import com.titan.product.orderservice.dto.OrderApiModels.OrderSummary;
import com.titan.product.orderservice.dto.OrderApiModels.PageResponse;
import com.titan.product.orderservice.service.ProductVariantProvider;
import com.titan.product.orderservice.service.ProductVariantProvider.ProductVariant;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orders;
    @Mock private OrderItemRepository items;
    @Mock private ProductVariantProvider variants;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, items, variants, new ObjectMapper());
    }

    @Test
    void createRejectsDuplicateVariants() {
        UUID v = UUID.randomUUID();
        CreateOrderRequest request =
                new CreateOrderRequest(
                        List.of(new ItemRequest(v, 1), new ItemRequest(v, 2)),
                        Map.of("line1", "x"),
                        null,
                        null);

        Assertions.assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(OrderApiException.class)
                .hasMessage("Each variant may appear only once")
                .extracting(e -> ((OrderApiException) e).status())
                .isEqualTo(422);

        verifyNoInteractions(variants, orders, items);
    }

    @Test
    void createRejectsMixedCurrencies() {
        UUID usd = UUID.randomUUID();
        UUID eur = UUID.randomUUID();
        when(variants.getActiveVariant(usd)).thenReturn(variant(usd, "USD", "10.00"));
        when(variants.getActiveVariant(eur)).thenReturn(variant(eur, "EUR", "10.00"));

        CreateOrderRequest request = requestOf(usd, eur);

        Assertions.assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(OrderApiException.class)
                .hasMessage("Currencies must match");

        verify(orders, never()).insert(any());
        verify(items, never()).insert(any());
    }

    @Test
    void createDoesNotPersistWhenVariantLookupFails() {
        UUID ok = UUID.randomUUID();
        UUID failing = UUID.randomUUID();
        when(variants.getActiveVariant(ok)).thenReturn(variant(ok, "USD", "10.00"));
        when(variants.getActiveVariant(failing))
                .thenThrow(new DependencyUnavailableException("down"));

        CreateOrderRequest request = requestOf(ok, failing);

        Assertions.assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(DependencyUnavailableException.class);

        verify(orders, never()).insert(any());
        verify(items, never()).insert(any());
    }

    @Test
    void createCalculatesSubtotalCorrectly() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(variants.getActiveVariant(a)).thenReturn(variant(a, "USD", "10.00"));
        when(variants.getActiveVariant(b)).thenReturn(variant(b, "USD", "2.50"));

        CreateOrderRequest request =
                new CreateOrderRequest(
                        List.of(new ItemRequest(a, 3), new ItemRequest(b, 4)),
                        Map.of("line1", "x"),
                        null,
                        null);

        service.create(UUID.randomUUID(), request);

        OrderEntity persisted = captureOrder();
        Assertions.assertThat(persisted.subtotalAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void createBuildsCorrectOrderSnapshot() {
        UUID v = UUID.randomUUID();
        when(variants.getActiveVariant(v))
                .thenReturn(
                        new ProductVariant(
                                v,
                                UUID.randomUUID(),
                                "Mechanical Keyboard",
                                "US Layout",
                                "KB-US-001",
                                new BigDecimal("149.99"),
                                "USD",
                                "{\"switch\":\"brown\"}"));

        CreateOrderRequest request =
                new CreateOrderRequest(
                        List.of(new ItemRequest(v, 2)),
                        Map.of("line1", "123 Main St"),
                        null,
                        "deliver fast");

        OrderResponse response = service.create(UUID.randomUUID(), request);

        OrderItemEntity item = captureItem();
        Assertions.assertThat(item.productName()).isEqualTo("Mechanical Keyboard");
        Assertions.assertThat(item.variantName()).isEqualTo("US Layout");
        Assertions.assertThat(item.sku()).isEqualTo("KB-US-001");
        Assertions.assertThat(item.attributes()).contains("brown");
        Assertions.assertThat(item.unitPrice()).isEqualByComparingTo("149.99");
        Assertions.assertThat(item.quantity()).isEqualTo(2);
        Assertions.assertThat(response.items()).hasSize(1);
        Assertions.assertThat(response.items().get(0).lineTotal()).isEqualByComparingTo("299.98");
    }

    @Test
    void createMapsShippingAndBillingAddresses() {
        UUID v = UUID.randomUUID();
        when(variants.getActiveVariant(v)).thenReturn(variant(v, "USD", "1.00"));

        CreateOrderRequest request =
                new CreateOrderRequest(
                        List.of(new ItemRequest(v, 1)),
                        Map.of("line1", "123 Main St", "city", "Srinagar"),
                        Map.of("line1", "456 Elm St"),
                        null);

        OrderResponse response = service.create(UUID.randomUUID(), request);

        Assertions.assertThat(response.shippingAddress())
                .containsEntry("line1", "123 Main St")
                .containsEntry("city", "Srinagar");
        Assertions.assertThat(response.billingAddress()).containsEntry("line1", "456 Elm St");
    }

    @Test
    void createAcceptsNullBillingAddress() {
        UUID v = UUID.randomUUID();
        when(variants.getActiveVariant(v)).thenReturn(variant(v, "USD", "1.00"));

        OrderResponse response =
                service.create(
                        UUID.randomUUID(),
                        new CreateOrderRequest(
                                List.of(new ItemRequest(v, 1)), Map.of("line1", "x"), null, null));

        Assertions.assertThat(response.billingAddress()).isNull();
    }

    @Test
    void createPreservesRequestedItemOrder() {
        List<UUID> requested = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            UUID v = UUID.randomUUID();
            requested.add(v);
            when(variants.getActiveVariant(v)).thenReturn(variant(v, "USD", "1.00"));
        }

        service.create(UUID.randomUUID(), requestOf(requested.toArray(UUID[]::new)));

        ArgumentCaptor<OrderItemEntity> captor = ArgumentCaptor.forClass(OrderItemEntity.class);
        verify(items, times(4)).insert(captor.capture());

        Assertions.assertThat(captor.getAllValues())
                .extracting(OrderItemEntity::position)
                .containsExactly((short) 0, (short) 1, (short) 2, (short) 3);
        Assertions.assertThat(captor.getAllValues())
                .extracting(OrderItemEntity::variantId)
                .containsExactlyElementsOf(requested);
    }

    @Test
    void createPropagatesPersistenceFailure() {
        UUID v = UUID.randomUUID();
        when(variants.getActiveVariant(v)).thenReturn(variant(v, "USD", "1.00"));
        doThrow(new OrderCheckConstraintViolationException(mock(PSQLException.class)))
                .when(orders)
                .insert(any());

        Assertions.assertThatThrownBy(() -> service.create(UUID.randomUUID(), requestOf(v)))
                .isInstanceOf(OrderCheckConstraintViolationException.class);

        verify(items, never()).insert(any());
    }

    @Test
    void getThrowsWhenOrderDoesNotExist() {
        when(orders.findByIdForUser(any(), any())).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> service.get(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order not found");

        verify(items, never()).findByOrderId(any());
    }

    @Test
    void getReturnsOrderWithItems() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orders.findByIdForUser(orderId, userId))
                .thenReturn(Optional.of(order(orderId, userId, OrderStatus.PAID)));
        when(items.findByOrderId(orderId))
                .thenReturn(List.of(item(orderId, 0, "10.00", 2), item(orderId, 1, "5.00", 3)));

        OrderResponse response = service.get(userId, orderId);

        Assertions.assertThat(response.id()).isEqualTo(orderId);
        Assertions.assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        Assertions.assertThat(response.items()).hasSize(2);
        Assertions.assertThat(response.items())
                .extracting(ItemResponse::lineTotal)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("20.00"), new BigDecimal("15.00"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -100, Integer.MIN_VALUE})
    void listRejectsInvalidPaginationPage(int page) {
        Assertions.assertThatThrownBy(() -> service.list(UUID.randomUUID(), page, 20))
                .isInstanceOf(OrderApiException.class)
                .extracting(e -> ((OrderApiException) e).status())
                .isEqualTo(400);

        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101, 1000})
    void listRejectsInvalidPaginationSize(int size) {
        Assertions.assertThatThrownBy(() -> service.list(UUID.randomUUID(), 0, size))
                .isInstanceOf(OrderApiException.class)
                .extracting(e -> ((OrderApiException) e).status())
                .isEqualTo(400);
    }

    @Test
    void listReturnsCorrectPageMetadata() {
        UUID userId = UUID.randomUUID();
        when(orders.findPageByUser(eq(userId), eq(0), eq(2)))
                .thenReturn(
                        List.of(
                                pageRow(userId, OrderStatus.PENDING, 3),
                                pageRow(userId, OrderStatus.PAID, 1)));
        when(orders.countByUser(userId)).thenReturn(5L);

        PageResponse<OrderSummary> page = service.list(userId, 0, 2);

        Assertions.assertThat(page.page()).isZero();
        Assertions.assertThat(page.size()).isEqualTo(2);
        Assertions.assertThat(page.totalElements()).isEqualTo(5);
        Assertions.assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void listHandlesLastPartialPage() {
        UUID userId = UUID.randomUUID();
        when(orders.findPageByUser(eq(userId), eq(2), eq(2)))
                .thenReturn(List.of(pageRow(userId, OrderStatus.PAID, 1)));
        when(orders.countByUser(userId)).thenReturn(5L);

        PageResponse<OrderSummary> page = service.list(userId, 2, 2);

        Assertions.assertThat(page.content()).hasSize(1);
        Assertions.assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void listReturnsZeroTotalPagesWhenNoOrders() {
        UUID userId = UUID.randomUUID();
        when(orders.findPageByUser(eq(userId), eq(0), eq(20))).thenReturn(List.of());
        when(orders.countByUser(userId)).thenReturn(0L);

        PageResponse<OrderSummary> page = service.list(userId, 0, 20);

        Assertions.assertThat(page.content()).isEmpty();
        Assertions.assertThat(page.totalElements()).isZero();
        Assertions.assertThat(page.totalPages()).isZero();
    }

    @Test
    void listMapsOrderSummaries() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orders.findPageByUser(eq(userId), eq(0), eq(20)))
                .thenReturn(
                        List.of(
                                new OrderRepository.OrderPageRow(
                                        order(orderId, userId, OrderStatus.SHIPPED), 7)));
        when(orders.countByUser(userId)).thenReturn(1L);

        OrderSummary summary = service.list(userId, 0, 20).content().get(0);

        Assertions.assertThat(summary.id()).isEqualTo(orderId);
        Assertions.assertThat(summary.status()).isEqualTo(OrderStatus.SHIPPED);
        Assertions.assertThat(summary.itemCount()).isEqualTo(7);
        Assertions.assertThat(summary.currency()).isEqualTo("USD");
        Assertions.assertThat(summary.totalAmount()).isEqualByComparingTo("69.99");
    }

    @Test
    void cancelThrowsWhenOrderDoesNotExist() {
        when(orders.findByIdForUser(any(), any())).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(
                        () ->
                                service.cancel(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        new CancelOrderRequest("x")))
                .isInstanceOf(NotFoundException.class);

        verify(orders, never()).cancel(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "PAID",
                "FULFILLMENT",
                "SHIPPED",
                "DELIVERED",
                "CANCELLED",
                "REFUNDED",
                "PAYMENT_PROCESSING"
            })
    void cancelRejectsNonPendingOrder(OrderStatus status) {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orders.findByIdForUser(orderId, userId))
                .thenReturn(Optional.of(order(orderId, userId, status)));

        Assertions.assertThatThrownBy(
                        () -> service.cancel(userId, orderId, new CancelOrderRequest("x")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be cancelled");

        verify(orders, never()).cancel(any(), any(), any());
    }

    @Test
    void cancelUpdatesPendingOrder() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orders.findByIdForUser(orderId, userId))
                .thenReturn(Optional.of(order(orderId, userId, OrderStatus.PENDING)))
                .thenReturn(Optional.of(order(orderId, userId, OrderStatus.CANCELLED)));
        when(items.findByOrderId(orderId)).thenReturn(List.of());

        OrderResponse response =
                service.cancel(userId, orderId, new CancelOrderRequest("changed mind"));

        verify(orders).cancel(eq(orderId), eq(userId), any(OffsetDateTime.class));
        Assertions.assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelUsesCorrectUserAndOrderIds() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orders.findByIdForUser(orderId, userId))
                .thenReturn(Optional.of(order(orderId, userId, OrderStatus.PENDING)))
                .thenReturn(Optional.of(order(orderId, userId, OrderStatus.CANCELLED)));
        when(items.findByOrderId(orderId)).thenReturn(List.of());

        service.cancel(userId, orderId, new CancelOrderRequest("x"));

        ArgumentCaptor<UUID> orderIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(orders).cancel(orderIdCaptor.capture(), userIdCaptor.capture(), any());

        Assertions.assertThat(orderIdCaptor.getValue()).isEqualTo(orderId);
        Assertions.assertThat(userIdCaptor.getValue()).isEqualTo(userId);
    }

    private OrderEntity captureOrder() {
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orders).insert(captor.capture());
        return captor.getValue();
    }

    private OrderItemEntity captureItem() {
        ArgumentCaptor<OrderItemEntity> captor = ArgumentCaptor.forClass(OrderItemEntity.class);
        verify(items).insert(captor.capture());
        return captor.getValue();
    }

    private static ProductVariant variant(UUID id, String currency, String price) {
        return new ProductVariant(
                id,
                UUID.randomUUID(),
                "Product",
                "Variant",
                "SKU-" + id,
                new BigDecimal(price),
                currency,
                "{}");
    }

    private static CreateOrderRequest requestOf(UUID... variantIds) {
        return new CreateOrderRequest(
                java.util.Arrays.stream(variantIds).map(v -> new ItemRequest(v, 1)).toList(),
                Map.of("line1", "x"),
                null,
                null);
    }

    private static OrderEntity order(UUID id, UUID userId, OrderStatus status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        return new OrderEntity(
                id,
                userId,
                status,
                "USD",
                new BigDecimal("19.99"),
                BigDecimal.ZERO,
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("69.99"),
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
                now);
    }

    private static OrderItemEntity item(UUID orderId, int position, String unitPrice, int qty) {
        return new OrderItemEntity(
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Product",
                "Variant",
                "SKU",
                "{}",
                qty,
                new BigDecimal(unitPrice),
                (short) position,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"));
    }

    private static OrderRepository.OrderPageRow pageRow(
            UUID userId, OrderStatus status, int itemCount) {
        return new OrderRepository.OrderPageRow(
                order(UUID.randomUUID(), userId, status), itemCount);
    }
}

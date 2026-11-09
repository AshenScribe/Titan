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
import com.titan.product.orderservice.data.entity.OrderItemEntity;
import com.titan.product.orderservice.data.exception.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
@Import({TestcontainersConfiguration.class, OrderRepository.class, OrderItemRepository.class})
@Sql(
        scripts = "/db/migration/V1__create_orders.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class OrderItemRepositoryTest {

    private static final OffsetDateTime TEST_TIME =
            OffsetDateTime.of(2026, 10, 4, 15, 45, 30, 0, ZoneOffset.UTC);

    @Autowired private OrderItemRepository orderItemRepository;

    @Autowired private OrderRepository orderRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void insertAndFindByIdRoundTripsEveryField() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .productName("Mechanical Keyboard")
                        .variantName("US Layout")
                        .sku("KB-US-001")
                        .attributes("{\"switch\":\"brown\",\"layout\":\"us\"}")
                        .quantity(3)
                        .unitPrice(new BigDecimal("149.99"))
                        .position((short) 4)
                        .createdAt(TEST_TIME)
                        .build();

        orderItemRepository.insert(item);

        OrderItemEntity found = orderItemRepository.findById(item.id()).orElseThrow();

        Assertions.assertThat(found.id()).isEqualTo(item.id());
        Assertions.assertThat(found.orderId()).isEqualTo(parent.id());
        Assertions.assertThat(found.productId()).isEqualTo(item.productId());
        Assertions.assertThat(found.variantId()).isEqualTo(item.variantId());
        Assertions.assertThat(found.productName()).isEqualTo("Mechanical Keyboard");
        Assertions.assertThat(found.variantName()).isEqualTo("US Layout");
        Assertions.assertThat(found.sku()).isEqualTo("KB-US-001");
        Assertions.assertThat(found.quantity()).isEqualTo(3);
        Assertions.assertThat(found.unitPrice()).isEqualByComparingTo("149.99");
        Assertions.assertThat(found.position()).isEqualTo((short) 4);
        Assertions.assertThat(found.createdAt()).isEqualTo(TEST_TIME);
    }

    @Test
    void insertAcceptsNullVariantName() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem().orderId(parent.id()).variantName(null).build();

        Assertions.assertThatCode(() -> orderItemRepository.insert(item))
                .doesNotThrowAnyException();
    }

    @Test
    void insertAcceptsEmptyJsonObjectAttributes() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem().orderId(parent.id()).attributes("{}").build();

        orderItemRepository.insert(item);

        Assertions.assertThat(orderItemRepository.findById(item.id()).orElseThrow().attributes())
                .isEqualTo("{}");
    }

    @Test
    void insertAcceptsNestedJsonbAttributes() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        String attributes =
                """
                {
                    "color": "black",
                    "specs": {"weight": 1.2, "tags": ["wireless", "noise-cancel"]}
                }
                """;
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .attributes(attributes)
                        .build();

        orderItemRepository.insert(item);

        OrderItemEntity found = orderItemRepository.findById(item.id()).orElseThrow();
        Assertions.assertThat(found.attributes())
                .contains("\"color\": \"black\"")
                .contains("\"weight\": 1.2")
                .contains("\"tags\"");
    }

    @Test
    void insertRoundsUnitPriceToColumnScale() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .unitPrice(new BigDecimal("10.456"))
                        .build();

        orderItemRepository.insert(item);

        Assertions.assertThat(orderItemRepository.findById(item.id()).orElseThrow().unitPrice())
                .isEqualByComparingTo("10.46");
    }

    @Test
    void insertAcceptsZeroUnitPrice() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .unitPrice(BigDecimal.ZERO)
                        .build();

        Assertions.assertThatCode(() -> orderItemRepository.insert(item))
                .doesNotThrowAnyException();
    }

    @Test
    void insertAcceptsMinimumQuantity() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem().orderId(parent.id()).quantity(1).build();

        Assertions.assertThatCode(() -> orderItemRepository.insert(item))
                .doesNotThrowAnyException();
    }

    @Test
    void insertAcceptsPositionEqualToShortMaxValue() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .position(Short.MAX_VALUE)
                        .build();

        Assertions.assertThatCode(() -> orderItemRepository.insert(item))
                .doesNotThrowAnyException();
    }

    @Test
    void insertDoesNotRequireVariantIdToReferenceAnyTable() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .variantId(UUID.randomUUID())
                        .build();

        Assertions.assertThatCode(() -> orderItemRepository.insert(item))
                .doesNotThrowAnyException();
    }

    @Test
    void insertFailsWhenOrderDoesNotExist() {
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem().orderId(UUID.randomUUID()).build();

        Assertions.assertThatThrownBy(() -> orderItemRepository.insert(item))
                .isInstanceOf(OrderForeignKeyViolationException.class);
    }

    @Test
    void insertFailsOnNullId() {
        assertNotNullViolation(OrderItemEntityBuilder.anOrderItem().id(null));
    }

    @Test
    void insertFailsOnNullProductId() {
        assertNotNullViolation(OrderItemEntityBuilder.anOrderItem().productId(null));
    }

    @Test
    void insertFailsOnNullVariantId() {
        assertNotNullViolation(OrderItemEntityBuilder.anOrderItem().variantId(null));
    }

    @Test
    void insertFailsOnNullProductName() {
        assertNotNullViolation(OrderItemEntityBuilder.anOrderItem().productName(null));
    }

    @Test
    void insertFailsOnNullSku() {
        assertNotNullViolation(OrderItemEntityBuilder.anOrderItem().sku(null));
    }

    @Test
    void insertFailsOnNullAttributes() {
        assertNotNullViolation(OrderItemEntityBuilder.anOrderItem().attributes(null));
    }

    @Test
    void insertFailsOnNullUnitPrice() {
        assertNotNullViolation(OrderItemEntityBuilder.anOrderItem().unitPrice(null));
    }

    @Test
    void insertFailsOnNullCreatedAt() {
        assertNotNullViolation(OrderItemEntityBuilder.anOrderItem().createdAt(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"hello\"", "123", "true", "false", "null"})
    void insertFailsOnNonObjectAttributes(String attributes) {
        assertCheckViolation(OrderItemEntityBuilder.anOrderItem().attributes(attributes));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100, Integer.MIN_VALUE})
    void insertFailsOnNonPositiveQuantity(int quantity) {
        assertCheckViolation(OrderItemEntityBuilder.anOrderItem().quantity(quantity));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1", "-100.00", "-999999.99"})
    void insertFailsOnNegativeUnitPrice(String amount) {
        assertCheckViolation(
                OrderItemEntityBuilder.anOrderItem().unitPrice(new BigDecimal(amount)));
    }

    @ParameterizedTest
    @ValueSource(shorts = {-1, -2, -100, Short.MIN_VALUE})
    void insertFailsOnNegativePosition(short position) {
        assertCheckViolation(OrderItemEntityBuilder.anOrderItem().position(position));
    }

    @Test
    void insertFailsOnDuplicateOrderAndVariantPair() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        UUID variantId = UUID.randomUUID();
        OrderItemEntity first =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .variantId(variantId)
                        .position((short) 0)
                        .build();
        orderItemRepository.insert(first);

        OrderItemEntity duplicateVariant =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .variantId(variantId)
                        .position((short) 1)
                        .build();

        Assertions.assertThatThrownBy(() -> orderItemRepository.insert(duplicateVariant))
                .isInstanceOf(DuplicateOrderItemException.class)
                .hasMessageContaining("Duplicate order item");
    }

    @Test
    void insertFailsOnDuplicateOrderAndPositionPair() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity first =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .position((short) 7)
                        .build();
        orderItemRepository.insert(first);

        OrderItemEntity duplicatePosition =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .position((short) 7)
                        .build();

        Assertions.assertThatThrownBy(() -> orderItemRepository.insert(duplicatePosition))
                .isInstanceOf(DuplicateOrderItemException.class);
    }

    @Test
    void allowsSameVariantInDifferentOrders() {
        OrderEntity firstParent = parentOrder();
        OrderEntity secondParent = parentOrder();
        orderRepository.insert(firstParent);
        orderRepository.insert(secondParent);
        UUID sharedVariant = UUID.randomUUID();
        OrderItemEntity first =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(firstParent.id())
                        .variantId(sharedVariant)
                        .build();
        OrderItemEntity second =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(secondParent.id())
                        .variantId(sharedVariant)
                        .build();

        Assertions.assertThatCode(
                        () -> {
                            orderItemRepository.insert(first);
                            orderItemRepository.insert(second);
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void allowsSamePositionInDifferentOrders() {
        OrderEntity firstParent = parentOrder();
        OrderEntity secondParent = parentOrder();
        orderRepository.insert(firstParent);
        orderRepository.insert(secondParent);
        OrderItemEntity first =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(firstParent.id())
                        .position((short) 0)
                        .build();
        OrderItemEntity second =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(secondParent.id())
                        .position((short) 0)
                        .build();

        Assertions.assertThatCode(
                        () -> {
                            orderItemRepository.insert(first);
                            orderItemRepository.insert(second);
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void insertFailsOnUnitPriceOverflow() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .unitPrice(new BigDecimal("10000000000.00"))
                        .build();

        Assertions.assertThatThrownBy(() -> orderItemRepository.insert(item))
                .isInstanceOf(OrderDataException.class);
    }

    @Test
    void findByIdReturnsEmptyForUnknownItem() {
        Assertions.assertThat(orderItemRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByOrderIdReturnsEmptyWhenOrderHasNoItems() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);

        Assertions.assertThat(orderItemRepository.findByOrderId(parent.id())).isEmpty();
    }

    @Test
    void findByOrderIdReturnsEmptyForUnknownOrder() {
        Assertions.assertThat(orderItemRepository.findByOrderId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByOrderIdReturnsOnlyItemsOfThatOrder() {
        OrderEntity firstParent = parentOrder();
        OrderEntity secondParent = parentOrder();
        orderRepository.insert(firstParent);
        orderRepository.insert(secondParent);
        OrderItemEntity firstItem =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(firstParent.id())
                        .position((short) 0)
                        .build();
        OrderItemEntity secondItem =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(secondParent.id())
                        .position((short) 0)
                        .build();
        orderItemRepository.insert(firstItem);
        orderItemRepository.insert(secondItem);

        List<OrderItemEntity> result = orderItemRepository.findByOrderId(firstParent.id());

        Assertions.assertThat(result)
                .extracting(OrderItemEntity::id)
                .containsExactly(firstItem.id());
    }

    @Test
    void findByOrderIdOrdersByPositionAscending() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity position3 =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .position((short) 3)
                        .build();
        OrderItemEntity position0 =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .position((short) 0)
                        .build();
        OrderItemEntity position2 =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .position((short) 2)
                        .build();
        OrderItemEntity position1 =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .position((short) 1)
                        .build();
        orderItemRepository.insert(position3);
        orderItemRepository.insert(position0);
        orderItemRepository.insert(position2);
        orderItemRepository.insert(position1);

        Assertions.assertThat(orderItemRepository.findByOrderId(parent.id()))
                .extracting(OrderItemEntity::id)
                .containsExactly(position0.id(), position1.id(), position2.id(), position3.id());
    }

    @Test
    void findByOrderIdHandlesNonContiguousPositions() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity low =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .position((short) 1)
                        .build();
        OrderItemEntity high =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .position((short) 100)
                        .build();
        orderItemRepository.insert(high);
        orderItemRepository.insert(low);

        Assertions.assertThat(orderItemRepository.findByOrderId(parent.id()))
                .extracting(OrderItemEntity::id)
                .containsExactly(low.id(), high.id());
    }

    @Test
    void findByOrderIdReturnsFullEntityShape() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity item =
                OrderItemEntityBuilder.anOrderItem()
                        .orderId(parent.id())
                        .position((short) 0)
                        .build();
        orderItemRepository.insert(item);

        OrderItemEntity found = orderItemRepository.findByOrderId(parent.id()).get(0);

        Assertions.assertThat(found.id()).isEqualTo(item.id());
        Assertions.assertThat(found.orderId()).isEqualTo(item.orderId());
        Assertions.assertThat(found.productId()).isEqualTo(item.productId());
        Assertions.assertThat(found.variantId()).isEqualTo(item.variantId());
        Assertions.assertThat(found.productName()).isEqualTo(item.productName());
        Assertions.assertThat(found.sku()).isEqualTo(item.sku());
        Assertions.assertThat(found.quantity()).isEqualTo(item.quantity());
        Assertions.assertThat(found.unitPrice()).isEqualByComparingTo(item.unitPrice());
        Assertions.assertThat(found.position()).isEqualTo(item.position());
        Assertions.assertThat(found.createdAt()).isEqualTo(item.createdAt());
    }

    @Test
    void deletingOrderCascadesToItsItems() {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        OrderItemEntity item = OrderItemEntityBuilder.anOrderItem().orderId(parent.id()).build();
        orderItemRepository.insert(item);

        jdbcTemplate.update("DELETE FROM orders WHERE id = ?", parent.id());

        Assertions.assertThat(orderItemRepository.findById(item.id())).isEmpty();
        Assertions.assertThat(orderItemRepository.findByOrderId(parent.id())).isEmpty();
    }

    private OrderEntity parentOrder() {
        return OrderEntityBuilder.anOrder().createdAt(TEST_TIME).build();
    }

    private void assertCheckViolation(OrderItemEntityBuilder builder) {
        OrderItemEntity item = withExistingOrder(builder).build();
        Assertions.assertThatThrownBy(() -> orderItemRepository.insert(item))
                .isInstanceOf(OrderCheckConstraintViolationException.class);
    }

    private void assertNotNullViolation(OrderItemEntityBuilder builder) {
        OrderItemEntity item = withExistingOrder(builder).build();
        Assertions.assertThatThrownBy(() -> orderItemRepository.insert(item))
                .isInstanceOf(OrderNotNullViolationException.class);
    }

    private OrderItemEntityBuilder withExistingOrder(OrderItemEntityBuilder builder) {
        OrderEntity parent = parentOrder();
        orderRepository.insert(parent);
        return builder.orderId(parent.id());
    }
}

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
import com.titan.product.orderservice.data.entity.OrderStatus;
import com.titan.product.orderservice.data.exception.DuplicateOrderException;
import com.titan.product.orderservice.data.exception.OrderCheckConstraintViolationException;
import com.titan.product.orderservice.data.exception.OrderDataException;
import com.titan.product.orderservice.data.exception.OrderNotNullViolationException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, OrderRepository.class})
@Sql(scripts = "/db/migration/V1__create_orders.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class OrderRepositoryTest {

    private static final OffsetDateTime TEST_TIME =
            OffsetDateTime.of(2026, 10, 4, 15, 45, 30, 0, ZoneOffset.UTC);

    @Autowired private OrderRepository orderRepository;

    @Test
    void insertAndFindById() {
        OrderEntity order = order(OrderStatus.PENDING, TEST_TIME);

        orderRepository.insert(order);

        OrderEntity found = orderRepository.findById(order.id()).orElseThrow();

        Assertions.assertThat(found.id()).isEqualTo(order.id());
        Assertions.assertThat(found.userId()).isEqualTo(order.userId());
        Assertions.assertThat(found.status()).isEqualTo(order.status());
        Assertions.assertThat(found.currency()).isEqualTo(order.currency());
        Assertions.assertThat(found.subtotalAmount()).isEqualByComparingTo(order.subtotalAmount());
        Assertions.assertThat(found.totalAmount()).isEqualByComparingTo("115.00");
        Assertions.assertThat(found.shippingAddress()).contains("123 Main St");
        Assertions.assertThat(found.billingAddress()).contains("456 Elm St");
        Assertions.assertThat(found.createdAt()).isEqualTo(order.createdAt());
        Assertions.assertThat(found.updatedAt()).isEqualTo(order.updatedAt());
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void insertAndRetrieveEveryOrderStatus(OrderStatus status) {
        OrderEntity order = order(status, TEST_TIME);

        orderRepository.insert(order);

        OrderEntity found = orderRepository.findById(order.id()).orElseThrow();

        Assertions.assertThat(found.status()).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "USD", "INR", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY", "SGD", "AED", "SAR",
                "NZD", "ZAR", "BRL"
            })
    void insertAcceptsValidCurrencyFormat(String currency) {
        OrderEntity order = OrderEntityBuilder.anOrder().currency(currency).build();

        Assertions.assertThatCode(() -> orderRepository.insert(order)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "usd", "Usd", "uSD", "inr", "123", "U1D", "1SD", "12D", "#ab", "US$", "U@D", "U-D",
                "U_D", "U/D", "U.D", "U D", "U+D", "U!D", "U%D", "U&D", "U*D", "U=D", "U:D", "U;D",
                "U,D", "U'D", "U\"D", "ÜSD", "₹SD", "US€", "ÜSĐ", "US", "U", "AB", "USDD", "1234",
                "   ", "   USD", "USD   ", " USD", "0.0", "A\nB", "US\n"
            })
    void errorOnInvalidCurrency(String currency) {
        OrderEntity order = OrderEntityBuilder.anOrder().currency(currency).build();

        Assertions.assertThatThrownBy(() -> orderRepository.insert(order))
                .isInstanceOf(OrderCheckConstraintViolationException.class);
    }

    @ParameterizedTest
    @NullSource
    void errorOnNullCurrency(String currency) {
        OrderEntity order = OrderEntityBuilder.anOrder().currency(currency).build();

        Assertions.assertThatThrownBy(() -> orderRepository.insert(order))
                .isInstanceOf(OrderNotNullViolationException.class);
    }

    @ParameterizedTest
    @EmptySource
    void errorOnEmptyCurrency(String currency) {
        OrderEntity order = OrderEntityBuilder.anOrder().currency(currency).build();

        Assertions.assertThatThrownBy(() -> orderRepository.insert(order))
                .isInstanceOf(OrderCheckConstraintViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1", "-10.00", "-999999.99"})
    void errorOnNegativeSubtotal(String amount) {
        OrderEntity order =
                OrderEntityBuilder.anOrder().subtotalAmount(new BigDecimal(amount)).build();

        assertCheckViolation(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1", "-10.00"})
    void errorOnNegativeTax(String amount) {
        OrderEntity order = OrderEntityBuilder.anOrder().taxAmount(new BigDecimal(amount)).build();

        assertCheckViolation(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1", "-10.00"})
    void errorOnNegativeShippingAmount(String amount) {
        OrderEntity order =
                OrderEntityBuilder.anOrder().shippingAmount(new BigDecimal(amount)).build();

        assertCheckViolation(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1", "-10.00"})
    void errorOnNegativeDiscount(String amount) {
        OrderEntity order =
                OrderEntityBuilder.anOrder().discountAmount(new BigDecimal(amount)).build();

        assertCheckViolation(order);
    }

    @ParameterizedTest
    @MethodSource("invalidTotalArguments")
    void errorWhenGeneratedTotalIsNegative(
            String subtotal, String tax, String shipping, String discount) {

        OrderEntity order =
                OrderEntityBuilder.anOrder()
                        .subtotalAmount(new BigDecimal(subtotal))
                        .taxAmount(new BigDecimal(tax))
                        .shippingAmount(new BigDecimal(shipping))
                        .discountAmount(new BigDecimal(discount))
                        .build();

        assertCheckViolation(order);
    }

    static Stream<Arguments> invalidTotalArguments() {
        return Stream.of(
                Arguments.of("100", "10", "5", "115.01"),
                Arguments.of("100", "0", "0", "100.01"),
                Arguments.of("0", "0", "0", "0.01"),
                Arguments.of("10", "5", "5", "20.01"));
    }

    @Test
    void acceptsZeroAmounts() {
        OrderEntity order =
                OrderEntityBuilder.anOrder()
                        .subtotalAmount(BigDecimal.ZERO)
                        .taxAmount(BigDecimal.ZERO)
                        .shippingAmount(BigDecimal.ZERO)
                        .discountAmount(BigDecimal.ZERO)
                        .build();

        Assertions.assertThatCode(() -> orderRepository.insert(order)).doesNotThrowAnyException();
    }

    @Test
    void acceptsDiscountEqualToSubtotalPlusTaxAndShipping() {
        OrderEntity order =
                OrderEntityBuilder.anOrder()
                        .subtotalAmount(new BigDecimal("100"))
                        .taxAmount(new BigDecimal("10"))
                        .shippingAmount(new BigDecimal("5"))
                        .discountAmount(new BigDecimal("115"))
                        .build();

        Assertions.assertThatCode(() -> orderRepository.insert(order)).doesNotThrowAnyException();
    }

    @Test
    void retrievesAmountsWithTwoDecimalPlaces() {
        OrderEntity order =
                OrderEntityBuilder.anOrder()
                        .subtotalAmount(new BigDecimal("123.45"))
                        .taxAmount(new BigDecimal("12.34"))
                        .shippingAmount(new BigDecimal("5.67"))
                        .discountAmount(new BigDecimal("1.46"))
                        .build();

        orderRepository.insert(order);

        OrderEntity found = orderRepository.findById(order.id()).orElseThrow();

        Assertions.assertThat(found.totalAmount()).isEqualByComparingTo("140.00");
    }

    @Test
    void postgresRoundsAmountsToColumnScale() {
        OrderEntity order =
                OrderEntityBuilder.anOrder().subtotalAmount(new BigDecimal("100.456")).build();

        orderRepository.insert(order);

        OrderEntity found = orderRepository.findById(order.id()).orElseThrow();

        Assertions.assertThat(found.subtotalAmount()).isEqualByComparingTo("100.46");
    }

    @Test
    void errorOnNumericOverflow() {
        OrderEntity order =
                OrderEntityBuilder.anOrder()
                        .subtotalAmount(new BigDecimal("10000000000.00"))
                        .build();

        Assertions.assertThatThrownBy(() -> orderRepository.insert(order))
                .isInstanceOf(OrderDataException.class);
    }

    @Test
    void acceptsCustomerNoteAtMaximumLength() {
        OrderEntity order = OrderEntityBuilder.anOrder().customerNote("a".repeat(1000)).build();

        Assertions.assertThatCode(() -> orderRepository.insert(order)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"hello\"", "123", "true", "false"})
    void errorOnNonObjectShippingAddress(String address) {
        OrderEntity order = OrderEntityBuilder.anOrder().shippingAddress(address).build();

        assertCheckViolation(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"hello\"", "123", "true", "false"})
    void errorOnNonObjectBillingAddress(String address) {
        OrderEntity order = OrderEntityBuilder.anOrder().billingAddress(address).build();

        assertCheckViolation(order);
    }

    @Test
    void acceptsEmptyShippingAddressObject() {
        OrderEntity order = OrderEntityBuilder.anOrder().shippingAddress("{}").build();

        Assertions.assertThatCode(() -> orderRepository.insert(order)).doesNotThrowAnyException();
    }

    @Test
    void acceptsEmptyBillingAddressObject() {
        OrderEntity order = OrderEntityBuilder.anOrder().billingAddress("{}").build();

        Assertions.assertThatCode(() -> orderRepository.insert(order)).doesNotThrowAnyException();
    }

    @Test
    void acceptsNullBillingAddress() {
        OrderEntity order = OrderEntityBuilder.anOrder().billingAddress(null).build();

        Assertions.assertThatCode(() -> orderRepository.insert(order)).doesNotThrowAnyException();
    }

    @Test
    void persistsNestedJsonbAddress() {
        String address =
                """
                {
                    "line1": "123 Main Street",
                    "city": "Srinagar",
                    "coordinates": {
                        "latitude": 34.08,
                        "longitude": 74.83
                    },
                    "tags": ["home", "primary"]
                }
                """;

        OrderEntity order = OrderEntityBuilder.anOrder().shippingAddress(address).build();

        orderRepository.insert(order);

        OrderEntity found = orderRepository.findById(order.id()).orElseThrow();

        Assertions.assertThat(found.shippingAddress())
                .contains("Srinagar")
                .contains("coordinates")
                .contains("latitude")
                .contains("tags");
    }

    @Test
    void errorOnNullUserId() {
        OrderEntity order = OrderEntityBuilder.anOrder().userId(null).build();

        assertNotNullViolation(order);
    }

    @Test
    void errorOnNullStatus() {
        OrderEntity order = OrderEntityBuilder.anOrder().status(null).build();

        assertNotNullViolation(order);
    }

    @Test
    void errorOnNullSubtotal() {
        OrderEntity order = OrderEntityBuilder.anOrder().subtotalAmount(null).build();

        assertNotNullViolation(order);
    }

    @Test
    void errorOnNullShippingAddress() {
        OrderEntity order = OrderEntityBuilder.anOrder().shippingAddress(null).build();

        assertNotNullViolation(order);
    }

    @Test
    void errorOnNullCreatedAt() {
        OrderEntity order = OrderEntityBuilder.anOrder().createdAt(null).build();

        assertNotNullViolation(order);
    }

    @Test
    void errorOnNullUpdatedAt() {
        OrderEntity order = OrderEntityBuilder.anOrder().updatedAt(null).build();

        assertNotNullViolation(order);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, -2, -100, Long.MIN_VALUE})
    void errorOnNegativeVersion(long version) {
        OrderEntity order = OrderEntityBuilder.anOrder().version(version).build();

        assertCheckViolation(order);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 10, 100, Long.MAX_VALUE})
    void acceptsNonNegativeVersion(long version) {
        OrderEntity order = OrderEntityBuilder.anOrder().version(version).build();

        Assertions.assertThatCode(() -> orderRepository.insert(order)).doesNotThrowAnyException();
    }

    @Test
    void errorOnDuplicateOrderId() {
        OrderEntity order = order(OrderStatus.PENDING, TEST_TIME);

        orderRepository.insert(order);

        Assertions.assertThatThrownBy(() -> orderRepository.insert(order))
                .isInstanceOf(DuplicateOrderException.class);
    }

    @Test
    void allowsDifferentOrdersForSameUser() {
        UUID userId = UUID.randomUUID();

        OrderEntity first = OrderEntityBuilder.anOrder().userId(userId).build();

        OrderEntity second = OrderEntityBuilder.anOrder().userId(userId).build();

        Assertions.assertThatCode(
                        () -> {
                            orderRepository.insert(first);
                            orderRepository.insert(second);
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void findByIdReturnsEmptyForUnknownOrder() {
        Assertions.assertThat(orderRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByIdReturnsInsertedOrder() {
        OrderEntity order = order(OrderStatus.PAID, TEST_TIME);

        orderRepository.insert(order);

        Assertions.assertThat(orderRepository.findById(order.id())).isPresent();
    }

    @Test
    void findByStatusReturnsOnlyMatchingOrders() {
        OrderEntity pending1 = order(OrderStatus.PENDING, TEST_TIME);
        OrderEntity paid = order(OrderStatus.PAID, TEST_TIME.plusHours(1));
        OrderEntity pending2 = order(OrderStatus.PENDING, TEST_TIME.plusHours(2));

        orderRepository.insert(pending1);
        orderRepository.insert(paid);
        orderRepository.insert(pending2);

        List<OrderEntity> result = orderRepository.findByStatus(OrderStatus.PENDING);

        Assertions.assertThat(result)
                .extracting(OrderEntity::id)
                .containsExactly(pending2.id(), pending1.id());
    }

    @Test
    void findByStatusReturnsEmptyWhenNoOrdersMatch() {
        orderRepository.insert(order(OrderStatus.PAID, TEST_TIME));

        Assertions.assertThat(orderRepository.findByStatus(OrderStatus.PENDING)).isEmpty();
    }

    @Test
    void findByStatusReturnsEmptyWhenNoOrdersExist() {
        Assertions.assertThat(orderRepository.findByStatus(OrderStatus.PENDING)).isEmpty();
    }

    @Test
    void findByStatusOrdersEqualTimestampsDeterministically() {
        UUID smallerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID largerId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        OrderEntity first =
                OrderEntityBuilder.anOrder()
                        .id(smallerId)
                        .status(OrderStatus.PENDING)
                        .createdAt(TEST_TIME)
                        .build();

        OrderEntity second =
                OrderEntityBuilder.anOrder()
                        .id(largerId)
                        .status(OrderStatus.PENDING)
                        .createdAt(TEST_TIME)
                        .build();

        orderRepository.insert(first);
        orderRepository.insert(second);

        Assertions.assertThat(orderRepository.findByStatus(OrderStatus.PENDING))
                .extracting(OrderEntity::id)
                .containsExactly(largerId, smallerId);
    }

    @Test
    void findByDateRangeIncludesBothBounds() {
        OffsetDateTime start = TEST_TIME;
        OffsetDateTime end = start.plusHours(2);

        OrderEntity before = order(OrderStatus.PENDING, start.minusSeconds(1));
        OrderEntity atStart = order(OrderStatus.PAID, start);
        OrderEntity atEnd = order(OrderStatus.CANCELLED, end);
        OrderEntity after = order(OrderStatus.REFUNDED, end.plusSeconds(1));

        orderRepository.insert(before);
        orderRepository.insert(atStart);
        orderRepository.insert(atEnd);
        orderRepository.insert(after);

        Assertions.assertThat(orderRepository.findByDateRange(start, end))
                .extracting(OrderEntity::id)
                .containsExactly(atEnd.id(), atStart.id());
    }

    @Test
    void findByDateRangeReturnsEmptyWhenNoOrdersMatch() {
        orderRepository.insert(order(OrderStatus.PENDING, TEST_TIME.minusDays(2)));

        Assertions.assertThat(orderRepository.findByDateRange(TEST_TIME, TEST_TIME.plusDays(1)))
                .isEmpty();
    }

    @Test
    void findByDateRangeReturnsEmptyWhenNoOrdersExist() {
        Assertions.assertThat(orderRepository.findByDateRange(TEST_TIME, TEST_TIME.plusDays(1)))
                .isEmpty();
    }

    @Test
    void findByDateRangeIncludesOrderWhenStartEqualsEnd() {
        OrderEntity order = order(OrderStatus.PENDING, TEST_TIME);

        orderRepository.insert(order);

        Assertions.assertThat(orderRepository.findByDateRange(TEST_TIME, TEST_TIME))
                .extracting(OrderEntity::id)
                .containsExactly(order.id());
    }

    @Test
    void findByDateRangeRejectsInvertedBounds() {
        Assertions.assertThatThrownBy(
                        () -> orderRepository.findByDateRange(TEST_TIME.plusDays(1), TEST_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date must not be after end date");
    }

    @Test
    void findByDateRangeOrdersEqualTimestampsDeterministically() {
        UUID smallerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID largerId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        OrderEntity first = OrderEntityBuilder.anOrder().id(smallerId).createdAt(TEST_TIME).build();

        OrderEntity second = OrderEntityBuilder.anOrder().id(largerId).createdAt(TEST_TIME).build();

        orderRepository.insert(first);
        orderRepository.insert(second);

        Assertions.assertThat(
                        orderRepository.findByDateRange(
                                TEST_TIME.minusSeconds(1), TEST_TIME.plusSeconds(1)))
                .extracting(OrderEntity::id)
                .containsExactly(largerId, smallerId);
    }

    private void assertCheckViolation(OrderEntity order) {
        Assertions.assertThatThrownBy(() -> orderRepository.insert(order))
                .isInstanceOf(OrderCheckConstraintViolationException.class);
    }

    private void assertNotNullViolation(OrderEntity order) {
        Assertions.assertThatThrownBy(() -> orderRepository.insert(order))
                .isInstanceOf(OrderNotNullViolationException.class);
    }

    private OrderEntity order(OrderStatus status, OffsetDateTime createdAt) {

        return new OrderEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                "USD",
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(5),
                BigDecimal.ZERO,
                null,
                "{\"line1\":\"123 Main St\"}",
                "{\"line1\":\"456 Elm St\"}",
                "Please deliver between 9 AM and 5 PM",
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt);
    }
}

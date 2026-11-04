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

package com.titan.product.orderservice;
import com.titan.product.orderservice.data.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import com.titan.product.orderservice.data.entity.OrderEntity;
import com.titan.product.orderservice.data.entity.OrderStatus;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, OrderRepository.class})
@Sql(scripts = "/V1__create_orders.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class OrderRepositoryTest {

	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final OffsetDateTime TEST_TIME = OffsetDateTime.of(2026, 10, 4, 15, 45, 30, 0, ZoneOffset.UTC);

	@BeforeEach
	void clearOrders() {
		jdbcTemplate.update("DELETE FROM orders");
	}

	@Test
	void insertAndFindById() {
		OrderEntity order = order(OrderStatus.PENDING, TEST_TIME);
		orderRepository.insert(order);
		Optional<OrderEntity> result = orderRepository.findById(order.id());
		Assertions.assertTrue(result.isPresent());
		OrderEntity found = result.orElseThrow();
		Assertions.assertEquals(order.id(), found.id());
		Assertions.assertEquals(order.userId(), found.userId());
		Assertions.assertEquals(OrderStatus.PENDING, found.status());
		Assertions.assertEquals(order.currency(), found.currency());
		Assertions.assertEquals(0, order.subtotalAmount().compareTo(found.subtotalAmount()));
		Assertions.assertEquals(0, BigDecimal.valueOf(115).compareTo(found.totalAmount()));
		Assertions.assertEquals("{\"line1\": \"123 Main St\"}", found.shippingAddress());
		Assertions.assertEquals("{\"line1\": \"456 Elm St\"}", found.billingAddress());
		Assertions.assertEquals(order.createdAt(), found.createdAt());
		Assertions.assertEquals(order.updatedAt(), found.updatedAt());
	}

	@Test
	void findByIdReturnsEmptyForUnknownOrder() {
		Assertions.assertTrue(orderRepository.findById(UUID.randomUUID()).isEmpty());
	}

	@Test
	void findByStatusFiltersAndOrdersByCreationDateDescending() {
		OffsetDateTime oldest = TEST_TIME;
		OrderEntity olderPending = order(OrderStatus.PENDING, oldest);
		OrderEntity newestPending = order(OrderStatus.PENDING, oldest.plusHours(2));
		OrderEntity paid = order(OrderStatus.PAID, oldest.plusHours(3));
		orderRepository.insert(olderPending);
		orderRepository.insert(newestPending);
		orderRepository.insert(paid);

		List<OrderEntity> result = orderRepository.findByStatus(OrderStatus.PENDING);

		Assertions.assertEquals(List.of(newestPending.id(), olderPending.id()),
				result.stream().map(OrderEntity::id).toList());
		Assertions.assertTrue(result.stream().allMatch(order -> order.status() == OrderStatus.PENDING));
	}

	@Test
	void findByDateRangeIncludesBothBoundsAndOrdersByCreationDateDescending() {
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

		List<OrderEntity> result = orderRepository.findByDateRange(start, end);

		Assertions.assertEquals(List.of(atEnd.id(), atStart.id()),
				result.stream().map(OrderEntity::id).toList());
	}

	private OrderEntity order(OrderStatus status, OffsetDateTime createdAt) {
		return new OrderEntity(UUID.randomUUID(), UUID.randomUUID(), status, "USD",
				BigDecimal.valueOf(100), BigDecimal.valueOf(10), BigDecimal.valueOf(5),
				BigDecimal.ZERO, null, "{\"line1\":\"123 Main St\"}",
				"{\"line1\":\"456 Elm St\"}", "Please deliver between 9 AM and 5 PM",
				1L, null, null, null, null, null, null, null, createdAt, createdAt);
	}
}
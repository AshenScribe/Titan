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
import com.titan.product.orderservice.data.entity.OrderCacheEnvelope;
import com.titan.product.orderservice.data.entity.OrderCacheKey;
import com.titan.product.orderservice.data.entity.OrderChangedEvent;
import com.titan.product.orderservice.data.entity.OrderSnapshot;
import com.titan.product.orderservice.domain.service.OrderCacheService;
import com.titan.product.orderservice.config.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.titan.product.orderservice.data.entity.OrderEntity;
import com.titan.product.orderservice.data.entity.OrderStatus;
import com.titan.product.orderservice.data.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(properties = {
		"titan.cache.l2-enabled=true",
		"titan.cache.namespace=titan:test:order-cache",
		"titan.cache.l2-ttl=10m",
		"titan.cache.negative-ttl=10m"
})
@Import(TestcontainersConfiguration.class)
@Sql(scripts = "/V1__create_orders.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class OrderCacheServiceTest {

	@Autowired
	private OrderCacheService orderCacheService;
	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private Cache<String, OrderCacheEnvelope> l1;
	@Autowired
	private RedisTemplate<String, OrderCacheEnvelope> redisTemplate;
	@Autowired
	private OrderCacheProperties properties;

	@BeforeEach
	void clearState() {
		jdbcTemplate.update("DELETE FROM orders");
		l1.invalidateAll();
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
	}

	@AfterEach
	void closeRedisConnection() {
		redisTemplate.getConnectionFactory().getConnection().close();
	}

	@Test
	void loadsFromDatabaseAndPopulatesBothCacheLevels() {
		OrderEntity order = order(OrderStatus.PENDING);
		orderRepository.insert(order);

		Optional<OrderSnapshot> result = orderCacheService.getOrderById(order.id());

		Assertions.assertTrue(result.isPresent());
		Assertions.assertEquals(order.id(), result.orElseThrow().id());
		Assertions.assertEquals(order.userId(), result.orElseThrow().userId());
		Assertions.assertEquals(order.status(), result.orElseThrow().status());
		Assertions.assertEquals(0, BigDecimal.valueOf(115).compareTo(result.orElseThrow().totalAmount()));

		String key = key(order.id());
		Assertions.assertNotNull(l1.getIfPresent(key));
		Assertions.assertNotNull(redisTemplate.opsForValue().get(key));
		Assertions.assertTrue(redisTemplate.getExpire(key) > 0);
	}

	@Test
	void servesSubsequentReadsFromL1WithoutObservingDatabaseChanges() {
		OrderEntity order = order(OrderStatus.PENDING);
		orderRepository.insert(order);
		orderCacheService.getOrderById(order.id());

		jdbcTemplate.update("UPDATE orders SET status = ?::order_status WHERE id = ?",
				OrderStatus.PAID.getValue(), order.id());

		OrderSnapshot result = orderCacheService.getOrderById(order.id()).orElseThrow();

		Assertions.assertEquals(OrderStatus.PENDING, result.status());
	}

	@Test
	void servesRedisValueWhenL1IsEmpty() {
		OrderEntity order = order(OrderStatus.PENDING);
		orderRepository.insert(order);
		orderCacheService.getOrderById(order.id());
		l1.invalidateAll();

		jdbcTemplate.update("UPDATE orders SET status = ?::order_status WHERE id = ?",
				OrderStatus.PAID.getValue(), order.id());

		OrderSnapshot result = orderCacheService.getOrderById(order.id()).orElseThrow();

		Assertions.assertEquals(OrderStatus.PENDING, result.status());
		Assertions.assertNotNull(l1.getIfPresent(key(order.id())));
	}

	@Test
	void cachesMissingOrdersNegativelyAtBothLevels() {
		UUID orderId = UUID.randomUUID();

		Assertions.assertTrue(orderCacheService.getOrderById(orderId).isEmpty());

		String key = key(orderId);
		OrderCacheEnvelope cached = l1.getIfPresent(key);
		Assertions.assertNotNull(cached);
		Assertions.assertFalse(cached.found());
		Assertions.assertNull(cached.order());
		Assertions.assertNotNull(redisTemplate.opsForValue().get(key));
		Assertions.assertTrue(redisTemplate.getExpire(key) > 0);
	}

	@Test
	void evictRemovesL1AndRedisEntriesSoUpdatedOrderCanBeLoaded() {
		OrderEntity order = order(OrderStatus.PENDING);
		orderRepository.insert(order);
		orderCacheService.getOrderById(order.id());
		String key = key(order.id());

		jdbcTemplate.update("UPDATE orders SET status = ?::order_status WHERE id = ?",
				OrderStatus.PAID.getValue(), order.id());
		orderCacheService.evict(new OrderChangedEvent(order.id()));

		Assertions.assertNull(l1.getIfPresent(key));
		Assertions.assertNull(redisTemplate.opsForValue().get(key));
		Assertions.assertEquals(OrderStatus.PAID,
				orderCacheService.getOrderById(order.id()).orElseThrow().status());
	}

	@Test
	void evictionAlsoRemovesNegativeCacheEntry() {
		UUID orderId = UUID.randomUUID();
		orderCacheService.getOrderById(orderId);
		Assertions.assertNotNull(redisTemplate.opsForValue().get(key(orderId)));

		orderRepository.insert(order(OrderStatus.PENDING, orderId));
		orderCacheService.evict(new OrderChangedEvent(orderId));

		Assertions.assertEquals(orderId, orderCacheService.getOrderById(orderId).orElseThrow().id());
	}

	@Test
	void preservesSnapshotFieldsWhenRoundTrippingThroughRedis() {
		OrderEntity order = order(OrderStatus.CANCELLED);
		orderRepository.insert(order);
		orderCacheService.getOrderById(order.id());
		l1.invalidateAll();

		OrderSnapshot result = orderCacheService.getOrderById(order.id()).orElseThrow();

		Assertions.assertEquals(order.id(), result.id());
		Assertions.assertEquals(order.status(), result.status());
		Assertions.assertEquals(order.currency(), result.currency());
		Assertions.assertEquals("{\"line1\": \"123 Main St\"}", result.shippingAddress());
		Assertions.assertEquals("{\"line1\": \"456 Elm St\"}", result.billingAddress());
		Assertions.assertEquals(order.customerNote(), result.customerNote());
		Assertions.assertEquals(order.version(), result.version());
	}

	private String key(UUID orderId) {
		return OrderCacheKey.byId(properties, orderId);
	}

	private OrderEntity order(OrderStatus status) {
		return order(status, UUID.randomUUID());
	}

	private OrderEntity order(OrderStatus status, UUID id) {
		OffsetDateTime timestamp = OffsetDateTime.parse("2026-01-01T10:00:00Z");
		return new OrderEntity(id, UUID.randomUUID(), status, "USD",
				BigDecimal.valueOf(100), BigDecimal.valueOf(10), BigDecimal.valueOf(5),
				BigDecimal.ZERO, null, "{\"line1\":\"123 Main St\"}",
				"{\"line1\":\"456 Elm St\"}", "Leave at the door", 2L,
				null, null, null, null, null, null, null, timestamp, timestamp);
	}
}

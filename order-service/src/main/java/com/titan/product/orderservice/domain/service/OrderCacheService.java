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

import com.github.benmanes.caffeine.cache.Cache;
import com.titan.product.orderservice.config.OrderCacheProperties;
import com.titan.product.orderservice.data.entity.OrderCacheEnvelope;
import com.titan.product.orderservice.data.entity.OrderCacheKey;
import com.titan.product.orderservice.data.entity.OrderChangedEvent;
import com.titan.product.orderservice.data.entity.OrderEntity;
import com.titan.product.orderservice.data.entity.OrderSnapshot;
import com.titan.product.orderservice.data.repository.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderCacheService {
    private static final Logger LOG = LoggerFactory.getLogger(OrderCacheService.class);
    private final OrderRepository orderRepository;
    private final OrderCacheProperties orderCacheProperties;
    private final Cache<String, OrderCacheEnvelope> l1;
    private final RedisTemplate<String, OrderCacheEnvelope> redisTemplate;

    public OrderCacheService(
            OrderRepository orderRepository,
            OrderCacheProperties orderCacheProperties,
            Cache<String, OrderCacheEnvelope> l1,
            ObjectProvider<RedisTemplate<String, OrderCacheEnvelope>> redisTemplateProvider) {
        this.orderRepository = orderRepository;
        this.orderCacheProperties = orderCacheProperties;
        this.l1 = l1;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public Optional<OrderSnapshot> getOrderById(UUID orderId) {
        String key = OrderCacheKey.byId(orderCacheProperties, orderId);
        OrderCacheEnvelope local = l1.getIfPresent(key);
        if (local != null) {
            return Optional.ofNullable(local.order());
        }
        local = redisTemplate == null ? null : redisTemplate.opsForValue().get(key);
        if (local != null) {
            l1.put(key, local);
            return Optional.ofNullable(local.order());
        }
        Optional<OrderCacheEnvelope> remote = loadFromDatabase(orderId);
        if (remote.isPresent()) {
            OrderCacheEnvelope envelope = remote.get();
            cacheFound(key, envelope);
            return Optional.of(envelope.order());
        }
        cacheMissing(key, orderId);
        return Optional.empty();
    }

    private void cacheFound(String key, OrderCacheEnvelope envelope) {
        l1.put(key, envelope);
        if (orderCacheProperties.isL2Enabled() && redisTemplate != null) {
            redisTemplate.opsForValue().set(key, envelope, orderCacheProperties.getL2Ttl());
        }
    }

    private void cacheMissing(String key, UUID orderId) {
        OrderCacheEnvelope missing = OrderCacheEnvelope.missing(orderId);
        l1.put(key, missing);
        if (orderCacheProperties.isL2Enabled() && redisTemplate != null) {
            redisTemplate.opsForValue().set(key, missing, orderCacheProperties.getNegativeTtl());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void evict(OrderChangedEvent orderChangedEvent) {
        var orderId = orderChangedEvent.orderId();
        try {
            String key = OrderCacheKey.byId(orderCacheProperties, orderId);
            l1.invalidate(key);
            if (redisTemplate != null) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            LOG.error("Failed to evict order {} from cache", orderId, e);
        }
    }

    private Optional<OrderCacheEnvelope> loadFromDatabase(UUID orderId) {
        return orderRepository.findById(orderId).map(this::from);
    }

    private OrderCacheEnvelope from(OrderEntity orderEntity) {
        OrderSnapshot orderSnapshot =
                new OrderSnapshot(
                        orderEntity.id(),
                        orderEntity.userId(),
                        orderEntity.status(),
                        orderEntity.currency(),
                        orderEntity.subtotalAmount(),
                        orderEntity.taxAmount(),
                        orderEntity.shippingAmount(),
                        orderEntity.discountAmount(),
                        orderEntity.totalAmount(),
                        orderEntity.shippingAddress(),
                        orderEntity.billingAddress(),
                        orderEntity.customerNote(),
                        orderEntity.version(),
                        orderEntity.createdAt(),
                        orderEntity.updatedAt());
        return OrderCacheEnvelope.found(orderSnapshot);
    }
}

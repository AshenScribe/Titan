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

import com.titan.product.orderservice.data.entity.OrderEntity;
import com.titan.product.orderservice.data.entity.OrderItemEntity;
import com.titan.product.orderservice.data.entity.OrderStatus;
import com.titan.product.orderservice.data.repository.OrderItemRepository;
import com.titan.product.orderservice.data.repository.OrderRepository;
import com.titan.product.orderservice.domain.exception.ConflictException;
import com.titan.product.orderservice.domain.exception.NotFoundException;
import com.titan.product.orderservice.domain.exception.OrderApiException;
import com.titan.product.orderservice.dto.OrderApiModels;
import com.titan.product.orderservice.dto.OrderApiModels.CancelOrderRequest;
import com.titan.product.orderservice.dto.OrderApiModels.CreateOrderRequest;
import com.titan.product.orderservice.dto.OrderApiModels.OrderResponse;
import com.titan.product.orderservice.dto.OrderApiModels.OrderSummary;
import com.titan.product.orderservice.dto.OrderApiModels.PageResponse;
import com.titan.product.orderservice.service.ProductVariantProvider;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final ProductVariantProvider variants;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orders,
            OrderItemRepository items,
            ProductVariantProvider variants,
            ObjectMapper objectMapper) {
        this.orders = orders;
        this.items = items;
        this.variants = variants;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse create(UUID userId, CreateOrderRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID orderId = UUID.randomUUID();
        validateItems(request);
        OrderDraft draft = buildSnapshots(request, orderId, now);
        OrderEntity order = buildOrder(userId, request, draft, now);
        orders.insert(order);
        draft.snapshots().forEach(items::insert);
        return response(order, draft.snapshots());
    }

    private void validateItems(CreateOrderRequest request) {
        long distinctVariants =
                request.items().stream()
                        .map(OrderApiModels.ItemRequest::variantId)
                        .distinct()
                        .count();
        if (distinctVariants != request.items().size()) {
            throw new OrderApiException(422, "Each variant may appear only once");
        }
    }

    private OrderDraft buildSnapshots(
            CreateOrderRequest request, UUID orderId, OffsetDateTime now) {
        List<OrderItemEntity> snapshots = new java.util.ArrayList<>();
        String currency = null;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (int position = 0; position < request.items().size(); position++) {
            var requested = request.items().get(position);
            var variant = variants.getActiveVariant(requested.variantId());
            if (currency == null) {
                currency = variant.currency();
            }
            if (!currency.equals(variant.currency())) {
                throw new OrderApiException(422, "Currencies must match");
            }
            subtotal =
                    subtotal.add(
                            variant.unitPrice().multiply(BigDecimal.valueOf(requested.quantity())));
            snapshots.add(
                    new OrderItemEntity(
                            UUID.randomUUID(),
                            orderId,
                            variant.productId(),
                            variant.variantId(),
                            variant.productName(),
                            variant.variantName(),
                            variant.sku(),
                            variant.attributes(),
                            requested.quantity(),
                            variant.unitPrice(),
                            (short) position,
                            now));
        }
        return new OrderDraft(orderId, snapshots, currency, subtotal);
    }

    private OrderEntity buildOrder(
            UUID userId, CreateOrderRequest request, OrderDraft draft, OffsetDateTime now) {
        return new OrderEntity(
                draft.orderId(),
                userId,
                OrderStatus.PENDING,
                draft.currency(),
                draft.subtotal(),
                BigDecimal.ZERO,
                BigDecimal.valueOf(50),
                BigDecimal.ZERO,
                null,
                json(request.shippingAddress()),
                request.billingAddress() == null ? null : json(request.billingAddress()),
                request.customerNote(),
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

    private record OrderDraft(
            UUID orderId, List<OrderItemEntity> snapshots, String currency, BigDecimal subtotal) {}

    @Transactional(readOnly = true)
    public OrderResponse get(UUID userId, UUID orderId) {
        OrderEntity order =
                orders.findByIdForUser(orderId, userId)
                        .orElseThrow(() -> new NotFoundException("Order not found"));
        return response(order, items.findByOrderId(orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(UUID userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new OrderApiException(400, "Invalid pagination");
        }
        List<OrderSummary> content =
                orders.findPageByUser(userId, page, size).stream()
                        .map(
                                row ->
                                        new OrderSummary(
                                                row.order().id(),
                                                row.order().status(),
                                                row.order().totalAmount(),
                                                row.order().currency(),
                                                row.itemCount(),
                                                row.order().createdAt()))
                        .toList();
        long total = orders.countByUser(userId);
        return new PageResponse<>(
                content, page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional
    public OrderResponse cancel(UUID userId, UUID orderId, CancelOrderRequest request) {
        OrderEntity order =
                orders.findByIdForUser(orderId, userId)
                        .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.status() != OrderStatus.PENDING) {
            throw new ConflictException("Order cannot be cancelled from " + order.status());
        }
        orders.cancel(orderId, userId, OffsetDateTime.now());
        return get(userId, orderId);
    }

    private OrderResponse response(OrderEntity order, List<OrderItemEntity> itemEntities) {
        List<OrderApiModels.ItemResponse> itemResponses =
                itemEntities.stream()
                        .map(
                                item ->
                                        new OrderApiModels.ItemResponse(
                                                item.productId(),
                                                item.variantId(),
                                                item.productName(),
                                                item.variantName(),
                                                item.sku(),
                                                item.unitPrice(),
                                                item.quantity(),
                                                item.unitPrice()
                                                        .multiply(
                                                                BigDecimal.valueOf(
                                                                        item.quantity()))))
                        .toList();
        return new OrderResponse(
                order.id(),
                order.status(),
                order.currency(),
                order.subtotalAmount(),
                order.taxAmount(),
                order.shippingAmount(),
                order.discountAmount(),
                order.totalAmount(),
                readJson(order.shippingAddress()),
                readJson(order.billingAddress()),
                order.customerNote(),
                itemResponses,
                order.createdAt(),
                order.paidAt(),
                order.cancelledAt());
    }

    private String json(Map<String, Object> value) {
        return objectMapper.writeValueAsString(value);
    }

    private Map<String, Object> readJson(String value) {
        return value == null ? null : objectMapper.readValue(value, Map.class);
    }
}

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

import com.titan.product.orderservice.data.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public final class OrderItemEntityBuilder {

    private UUID id = UUID.randomUUID();
    private UUID orderId = UUID.randomUUID();
    private UUID productId = UUID.randomUUID();
    private UUID variantId = UUID.randomUUID();
    private String productName = "Wireless Headphones";
    private String variantName = "Midnight Black";
    private String sku = "WH-BLK-001";
    private String attributes = "{\"color\":\"black\"}";
    private int quantity = 1;
    private BigDecimal unitPrice = BigDecimal.valueOf(99, 2);
    private short position;
    private OffsetDateTime createdAt =
            OffsetDateTime.of(2026, 10, 4, 15, 45, 30, 0, ZoneOffset.UTC);

    private OrderItemEntityBuilder() {}

    public static OrderItemEntityBuilder anOrderItem() {
        return new OrderItemEntityBuilder();
    }

    public OrderItemEntityBuilder id(UUID value) {
        this.id = value;
        return this;
    }

    public OrderItemEntityBuilder orderId(UUID value) {
        this.orderId = value;
        return this;
    }

    public OrderItemEntityBuilder productId(UUID value) {
        this.productId = value;
        return this;
    }

    public OrderItemEntityBuilder variantId(UUID value) {
        this.variantId = value;
        return this;
    }

    public OrderItemEntityBuilder productName(String value) {
        this.productName = value;
        return this;
    }

    public OrderItemEntityBuilder variantName(String value) {
        this.variantName = value;
        return this;
    }

    public OrderItemEntityBuilder sku(String value) {
        this.sku = value;
        return this;
    }

    public OrderItemEntityBuilder attributes(String value) {
        this.attributes = value;
        return this;
    }

    public OrderItemEntityBuilder quantity(int value) {
        this.quantity = value;
        return this;
    }

    public OrderItemEntityBuilder unitPrice(BigDecimal value) {
        this.unitPrice = value;
        return this;
    }

    public OrderItemEntityBuilder position(short value) {
        this.position = value;
        return this;
    }

    public OrderItemEntityBuilder createdAt(OffsetDateTime value) {
        this.createdAt = value;
        return this;
    }

    public OrderItemEntity build() {
        return new OrderItemEntity(
                id,
                orderId,
                productId,
                variantId,
                productName,
                variantName,
                sku,
                attributes,
                quantity,
                unitPrice,
                position,
                createdAt);
    }
}

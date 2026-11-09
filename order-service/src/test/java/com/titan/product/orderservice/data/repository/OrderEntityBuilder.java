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

import com.titan.product.orderservice.data.entity.OrderEntity;
import com.titan.product.orderservice.data.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public final class OrderEntityBuilder {

    private UUID id = UUID.randomUUID();
    private UUID userId = UUID.randomUUID();
    private OrderStatus status = OrderStatus.PENDING;
    private String currency = "USD";
    private BigDecimal subtotalAmount = BigDecimal.valueOf(100);
    private BigDecimal taxAmount = BigDecimal.valueOf(10);
    private BigDecimal shippingAmount = BigDecimal.valueOf(5);
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount;
    private String shippingAddress = "{\"line1\":\"123 Main St\"}";
    private String billingAddress = "{\"line1\":\"456 Elm St\"}";
    private String customerNote = "Please deliver between 9 AM and 5 PM";
    private long version = 1L;
    private OffsetDateTime inventoryReservedAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime shippedAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime refundedAt;
    private OffsetDateTime createdAt =
            OffsetDateTime.of(2026, 10, 4, 15, 45, 30, 0, ZoneOffset.UTC);
    private OffsetDateTime updatedAt = createdAt;

    private OrderEntityBuilder() {}

    public static OrderEntityBuilder anOrder() {
        return new OrderEntityBuilder();
    }

    public static OrderEntity ofStatus(OrderStatus statusValue) {
        return anOrder().status(statusValue).build();
    }

    public static OrderEntity ofCreatedAt(OffsetDateTime createdAtValue) {
        return anOrder().createdAt(createdAtValue).updatedAt(createdAtValue).build();
    }

    public static OrderEntity ofStatusAndCreatedAt(
            OrderStatus statusValue, OffsetDateTime createdAtValue) {
        return anOrder()
                .status(statusValue)
                .createdAt(createdAtValue)
                .updatedAt(createdAtValue)
                .build();
    }

    public OrderEntityBuilder id(UUID idValue) {
        this.id = idValue;
        return this;
    }

    public OrderEntityBuilder userId(UUID userIdValue) {
        this.userId = userIdValue;
        return this;
    }

    public OrderEntityBuilder status(OrderStatus statusValue) {
        this.status = statusValue;
        return this;
    }

    public OrderEntityBuilder currency(String currencyValue) {
        this.currency = currencyValue;
        return this;
    }

    public OrderEntityBuilder subtotalAmount(BigDecimal subtotalAmountValue) {
        this.subtotalAmount = subtotalAmountValue;
        return this;
    }

    public OrderEntityBuilder taxAmount(BigDecimal taxAmountValue) {
        this.taxAmount = taxAmountValue;
        return this;
    }

    public OrderEntityBuilder shippingAmount(BigDecimal shippingAmountValue) {
        this.shippingAmount = shippingAmountValue;
        return this;
    }

    public OrderEntityBuilder discountAmount(BigDecimal discountAmountValue) {
        this.discountAmount = discountAmountValue;
        return this;
    }

    public OrderEntityBuilder totalAmount(BigDecimal totalAmountValue) {
        this.totalAmount = totalAmountValue;
        return this;
    }

    public OrderEntityBuilder shippingAddress(String shippingAddressValue) {
        this.shippingAddress = shippingAddressValue;
        return this;
    }

    public OrderEntityBuilder billingAddress(String billingAddressValue) {
        this.billingAddress = billingAddressValue;
        return this;
    }

    public OrderEntityBuilder customerNote(String customerNoteValue) {
        this.customerNote = customerNoteValue;
        return this;
    }

    public OrderEntityBuilder version(long versionValue) {
        this.version = versionValue;
        return this;
    }

    public OrderEntityBuilder inventoryReservedAt(OffsetDateTime inventoryReservedAtValue) {
        this.inventoryReservedAt = inventoryReservedAtValue;
        return this;
    }

    public OrderEntityBuilder paidAt(OffsetDateTime paidAtValue) {
        this.paidAt = paidAtValue;
        return this;
    }

    public OrderEntityBuilder confirmedAt(OffsetDateTime confirmedAtValue) {
        this.confirmedAt = confirmedAtValue;
        return this;
    }

    public OrderEntityBuilder shippedAt(OffsetDateTime shippedAtValue) {
        this.shippedAt = shippedAtValue;
        return this;
    }

    public OrderEntityBuilder deliveredAt(OffsetDateTime deliveredAtValue) {
        this.deliveredAt = deliveredAtValue;
        return this;
    }

    public OrderEntityBuilder cancelledAt(OffsetDateTime cancelledAtValue) {
        this.cancelledAt = cancelledAtValue;
        return this;
    }

    public OrderEntityBuilder refundedAt(OffsetDateTime refundedAtValue) {
        this.refundedAt = refundedAtValue;
        return this;
    }

    public OrderEntityBuilder createdAt(OffsetDateTime createdAtValue) {
        this.createdAt = createdAtValue;
        this.updatedAt = createdAt;
        return this;
    }

    public OrderEntityBuilder updatedAt(OffsetDateTime updatedAtValue) {
        this.updatedAt = updatedAtValue;
        return this;
    }

    public OrderEntity build() {
        return new OrderEntity(
                id,
                userId,
                status,
                currency,
                subtotalAmount,
                taxAmount,
                shippingAmount,
                discountAmount,
                totalAmount,
                shippingAddress,
                billingAddress,
                customerNote,
                version,
                inventoryReservedAt,
                paidAt,
                confirmedAt,
                shippedAt,
                deliveredAt,
                cancelledAt,
                refundedAt,
                createdAt,
                updatedAt);
    }
}

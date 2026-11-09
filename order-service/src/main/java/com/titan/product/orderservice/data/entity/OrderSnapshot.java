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

package com.titan.product.orderservice.data.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderSnapshot(
        UUID id,
        UUID userId,
        OrderStatus status,
        String currency,
        BigDecimal subtotalAmount,
        BigDecimal taxAmount,
        BigDecimal shippingAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String shippingAddress,
        String billingAddress,
        String customerNote,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public static OrderSnapshot from(OrderEntity entity) {
        return new OrderSnapshot(
                entity.id(),
                entity.userId(),
                entity.status(),
                entity.currency(),
                entity.subtotalAmount(),
                entity.taxAmount(),
                entity.shippingAmount(),
                entity.discountAmount(),
                entity.totalAmount(),
                entity.shippingAddress(),
                entity.billingAddress(),
                entity.customerNote(),
                entity.version(),
                entity.createdAt(),
                entity.updatedAt());
    }

    public OrderEntity toEntity() {
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                createdAt,
                updatedAt);
    }
}

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

import com.titan.product.orderservice.data.entity.OrderIdempotencyEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

final class OrderIdempotencyEntityBuilder {

    private static final OffsetDateTime DEFAULT_CREATED_AT =
            OffsetDateTime.of(2026, 10, 4, 15, 45, 30, 0, ZoneOffset.UTC);

    private UUID userId = UUID.randomUUID();
    private String idempotencyKey = "key-" + UUID.randomUUID();
    private byte[] requestHash = filledHash(0x11);
    private UUID orderId = UUID.randomUUID();
    private OffsetDateTime createdAt = DEFAULT_CREATED_AT;
    private OffsetDateTime expiresAt = DEFAULT_CREATED_AT.plusHours(1);

    private OrderIdempotencyEntityBuilder() {}

    static OrderIdempotencyEntityBuilder anIdempotencyRecord() {
        return new OrderIdempotencyEntityBuilder();
    }

    static byte[] filledHash(int value) {
        byte[] hash = new byte[32];
        Arrays.fill(hash, (byte) value);
        return hash;
    }

    OrderIdempotencyEntityBuilder userId(UUID value) {
        this.userId = value;
        return this;
    }

    OrderIdempotencyEntityBuilder idempotencyKey(String value) {
        this.idempotencyKey = value;
        return this;
    }

    OrderIdempotencyEntityBuilder requestHash(byte[] value) {
        this.requestHash = value;
        return this;
    }

    OrderIdempotencyEntityBuilder orderId(UUID value) {
        this.orderId = value;
        return this;
    }

    OrderIdempotencyEntityBuilder createdAt(OffsetDateTime value) {
        this.createdAt = value;
        return this;
    }

    OrderIdempotencyEntityBuilder expiresAt(OffsetDateTime value) {
        this.expiresAt = value;
        return this;
    }

    OrderIdempotencyEntity build() {
        return new OrderIdempotencyEntity(
                userId, idempotencyKey, requestHash, orderId, createdAt, expiresAt);
    }
}

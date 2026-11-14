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

package com.titan.payment.infrastructure.persistence.repository;

import com.titan.payment.infrastructure.persistence.model.GatewayPaymentEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class GatewayPaymentEntityBuilder {

    private UUID id = UUID.randomUUID();
    private UUID paymentAttemptId = UUID.randomUUID();
    private String gatewayPaymentId = "gw-" + UUID.randomUUID();
    private long amount = 1000L;
    private long amountRefunded = 0L;
    private String currency = "USD";
    private String status = "CREATED";
    private String method = "card";
    private boolean captured = false;
    private Map<String, Object> notes = Map.of();
    private OffsetDateTime gatewayCreatedAt = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt = OffsetDateTime.now();
    private long version = 0L;

    private GatewayPaymentEntityBuilder() {}

    public static GatewayPaymentEntityBuilder anEntity() {
        return new GatewayPaymentEntityBuilder();
    }

    public GatewayPaymentEntityBuilder withId(UUID id) { this.id = id; return this; }
    public GatewayPaymentEntityBuilder withPaymentAttemptId(UUID v) { this.paymentAttemptId = v; return this; }
    public GatewayPaymentEntityBuilder withGatewayPaymentId(String v) { this.gatewayPaymentId = v; return this; }
    public GatewayPaymentEntityBuilder withAmount(long v) { this.amount = v; return this; }
    public GatewayPaymentEntityBuilder withAmountRefunded(long v) { this.amountRefunded = v; return this; }
    public GatewayPaymentEntityBuilder withCurrency(String v) { this.currency = v; return this; }
    public GatewayPaymentEntityBuilder withStatus(String v) { this.status = v; return this; }
    public GatewayPaymentEntityBuilder withMethod(String v) { this.method = v; return this; }
    public GatewayPaymentEntityBuilder withCaptured(boolean v) { this.captured = v; return this; }
    public GatewayPaymentEntityBuilder withNotes(Map<String, Object> v) { this.notes = v; return this; }
    public GatewayPaymentEntityBuilder withGatewayCreatedAt(OffsetDateTime v) { this.gatewayCreatedAt = v; return this; }
    public GatewayPaymentEntityBuilder withCreatedAt(OffsetDateTime v) { this.createdAt = v; return this; }
    public GatewayPaymentEntityBuilder withUpdatedAt(OffsetDateTime v) { this.updatedAt = v; return this; }
    public GatewayPaymentEntityBuilder withVersion(long v) { this.version = v; return this; }

    public GatewayPaymentEntity build() {
        return new GatewayPaymentEntity(
                id, paymentAttemptId, gatewayPaymentId, amount, amountRefunded,
                currency, status, method, captured, notes,
                gatewayCreatedAt, createdAt, updatedAt, version);
    }
}

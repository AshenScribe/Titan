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

package com.titan.payment.domain.entity;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Your system's record of a single payment attempt against an order.
 * <p>
 * Distinct from {@link PaymentDetails}:
 * <ul>
 *   <li>{@code PaymentDetails} is the <em>gateway's view</em> — read-only, returned
 *       by {@code fetchPayment}/{@code fetchPaymentsForOrder}, used for reconciliation.</li>
 *   <li>{@code Payment} is <em>your view</em> — the entity you persist, update on
 *       webhooks, and attach business state to (fulfilment, refunds, disputes).</li>
 * </ul>
 * <p>
 * <p>
 * Equality is by {@code id} (the gateway payment id) when present; a {@code Payment}
 * with a null id is equal only to itself (it has no stable identity yet).
 * Immutable. Mutations happen by producing a new instance (see {@link #withStatus}).
 */
public final class Payment {

    private final String id;                 // gateway payment id (e.g. pay_xxx); null until gateway responds
    private final String orderId;            // your internal order id
    private final String gatewayOrderId;     // gateway order id (e.g. order_xxx)
    private final long amountInSubunits;
    private final long amountRefundedInSubunits;
    private final String currency;
    private final PaymentStatus status;
    private final String method;             // e.g. "card", "upi"; may be null before attempt completes
    private final boolean captured;
    private final Map<String, String> notes;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Payment(
            String id,
            String orderId,
            String gatewayOrderId,
            long amountInSubunits,
            long amountRefundedInSubunits,
            String currency,
            PaymentStatus status,
            String method,
            boolean captured,
            Map<String, String> notes,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (amountInSubunits <= 0) {
            throw new IllegalArgumentException("amountInSubunits must be positive");
        }
        this.id = id;
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.gatewayOrderId = gatewayOrderId;
        this.amountInSubunits = amountInSubunits;
        this.amountRefundedInSubunits = amountRefundedInSubunits;
        this.currency = Objects.requireNonNull(currency, "currency");
        this.status = Objects.requireNonNull(status, "status");
        this.method = method;
        this.captured = captured;
        this.notes = notes == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(notes));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Convenience factory for a brand-new attempt, before the gateway responds.
     */
    public static Payment pending( String orderId, long amountInSubunits, String currency,
                                   Map<String, String> notes, Instant now ) {
        return new Payment(
                null, orderId, null,
                amountInSubunits, 0L, currency,
                PaymentStatus.CREATED, null, false,
                notes, now, now);
    }

    public String id() {
        return id;
    }

    public String orderId() {
        return orderId;
    }

    public String gatewayOrderId() {
        return gatewayOrderId;
    }

    public long amountInSubunits() {
        return amountInSubunits;
    }

    public long amountRefundedInSubunits() {
        return amountRefundedInSubunits;
    }

    public String currency() {
        return currency;
    }

    public PaymentStatus status() {
        return status;
    }

    public String method() {
        return method;
    }

    public boolean captured() {
        return captured;
    }

    public Map<String, String> notes() {
        return notes;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Returns a copy with the given status and a bumped {@code updatedAt}.
     */
    public Payment withStatus( PaymentStatus newStatus, Instant now ) {
        return new Payment(
                id, orderId, gatewayOrderId,
                amountInSubunits, amountRefundedInSubunits, currency,
                newStatus, method, captured, notes,
                createdAt, now);
    }

    /**
     * Returns a copy hydrated from the gateway's report.
     */
    public Payment withGatewayDetails( PaymentDetails details, Instant now ) {
        return new Payment(
                details.id(), orderId, details.orderId(),
                details.amountInSubunits(), details.amountRefundedInSubunits(), details.currency(),
                details.status(), details.method(), details.captured(), details.notes(),
                createdAt, now);
    }

    @Override
    public boolean equals( Object o ) {
        if (this == o) return true;
        if (!( o instanceof Payment other )) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }
}
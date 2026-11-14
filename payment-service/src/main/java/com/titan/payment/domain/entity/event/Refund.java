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

package com.titan.payment.domain.entity.event;

import com.titan.payment.domain.entity.Payment;
import com.titan.payment.domain.entity.PaymentDetails;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Your system's record of a refund. Distinct from {@link RefundDetails}
 * (the gateway's report) in the same way {@link Payment} is distinct from
 * {@link PaymentDetails}: the gateway says what happened, this entity is what
 * you store and evolve (approval state, customer notification state, ledger
 * adjustment state).
 * <p>
 * Immutable. Mutations happen by producing a new instance via {@code with*}.
 * Equality is by {@code id} (the gateway refund id) when present; a {@code Refund}
 * with a null id is equal only to itself (it has no natural key yet).
 */
public final class Refund {

    private final String id;
    private final String paymentId;
    private final String orderId;
    private final long amountInSubunits;
    private final String currency;
    private final RefundStatus status;
    private final String speedProcessed;
    private final String speedRequested;
    private final boolean autoRefund;
    private final Map<String, String> notes;
    private final String acquirerReferenceNumber;
    private final String batchId;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Refund(
            String id,
            String paymentId,
            String orderId,
            long amountInSubunits,
            String currency,
            RefundStatus status,
            String speedProcessed,
            String speedRequested,
            boolean autoRefund,
            Map<String, String> notes,
            String acquirerReferenceNumber,
            String batchId,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (amountInSubunits <= 0) {
            throw new IllegalArgumentException("amountInSubunits must be positive, got " + amountInSubunits);
        }
        this.id = id;
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.orderId = orderId;
        this.amountInSubunits = amountInSubunits;
        this.currency = requireIsoCurrency(currency);
        this.status = Objects.requireNonNull(status, "status");
        this.speedProcessed = speedProcessed;
        this.acquirerReferenceNumber = acquirerReferenceNumber;
        this.batchId = batchId;
        this.version = version;
        this.speedRequested = speedRequested;
        this.autoRefund = autoRefund;
        this.notes = notes == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(notes));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public String id() {
        return id;
    }

    public String paymentId() {
        return paymentId;
    }

    public String orderId() {
        return orderId;
    }

    public String acquirerReferenceNumber() {
        return acquirerReferenceNumber;
    }

    public String batchId() {
        return batchId;
    }

    public long version() {
        return version;
    }

    public long amountInSubunits() {
        return amountInSubunits;
    }

    public String currency() {
        return currency;
    }

    public RefundStatus status() {
        return status;
    }

    public String speedProcessed() {
        return speedProcessed;
    }

    public String speedRequested() {
        return speedRequested;
    }

    public boolean autoRefund() {
        return autoRefund;
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
     * Hydrates this refund from the gateway's report.
     * <p>
     * If the stored refund already has an id, it must match the gateway's — a
     * mismatch means we're about to overwrite the wrong row, so fail loudly
     * instead of silently mutating the primary key.
     */
    public Refund withGatewayDetails( RefundDetails details, Instant now ) {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(now, "now");
        if (id != null && !id.equals(details.id())) {
            throw new IllegalStateException(
                    "refund id mismatch: stored=" + id + ", gateway=" + details.id());
        }

        return new Refund(
                details.id(), paymentId, orderId,
                details.amountInSubunits(), details.currency(),
                details.status(), details.speedProcessed(), details.speedRequested(),
                autoRefund, details.notes(),
                details.acquirerReferenceNumber(),
                details.batchId(),
                version,
                createdAt, now);
    }

    public Refund withVersion( long newVersion ) {
        return new Refund(
                id, paymentId, orderId, amountInSubunits, currency,
                status, speedProcessed, speedRequested,
                autoRefund, notes,
                acquirerReferenceNumber, batchId, newVersion,
                createdAt, updatedAt);
    }

    /**
     * Returns a copy with the given status and a bumped {@code updatedAt}.
     */
    public Refund withStatus( RefundStatus newStatus, Instant now ) {
        return new Refund(
                id, paymentId, orderId, amountInSubunits, currency,
                newStatus, speedProcessed, speedRequested,
                autoRefund, notes,
                acquirerReferenceNumber, batchId, version,
                createdAt, now);
    }

    /**
     * Returns a copy with the new processed speed and a bumped {@code updatedAt}.
     */
    public Refund withSpeed( String newSpeedProcessed, Instant now ) {
        return new Refund(
                id, paymentId, orderId, amountInSubunits, currency,
                status, newSpeedProcessed, speedRequested,
                autoRefund, notes,
                acquirerReferenceNumber, batchId, version,
                createdAt, now);
    }

    private static String requireIsoCurrency( String currency ) {
        Objects.requireNonNull(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException(
                    "currency must be a 3-letter ISO-4217 code, got: " + currency);
        }
        return currency.toUpperCase();
    }

    @Override
    public boolean equals( Object o ) {
        if (this == o) return true;
        if (!( o instanceof Refund other )) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }

    @Override
    public String toString() {
        return "Refund{" +
                "id='" + id + '\'' +
                ", paymentId='" + paymentId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", amountInSubunits=" + amountInSubunits +
                ", currency='" + currency + '\'' +
                ", status=" + status +
                ", speedProcessed='" + speedProcessed + '\'' +
                ", speedRequested='" + speedRequested + '\'' +
                ", autoRefund=" + autoRefund +
                ", notes=" + notes.size() + " entries" +
                '}';
    }
}
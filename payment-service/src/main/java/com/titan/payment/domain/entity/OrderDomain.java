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

import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The business's intent to charge a customer, expressed in gateway-neutral terms.
 * <p>
 * This is the <em>input</em> to {@link com.titan.payment.domain.service.PaymentService#createOrder}.
 * It carries only what the business knows before talking to a gateway: how much,
 * in what currency, under what internal reference, and with what free-form notes.
 * It deliberately contains no gateway ids, no status, and no gateway-specific
 * fields — those belong to {@link PaymentOrder} (the gateway's response) and
 * {@link Payment} (your system's record).
 * <p>
 * Instances are immutable and validated at construction time. Validation mirrors
 * the constraints documented by Razorpay's Orders API
 * (<a href="https://razorpay.com/docs/api/orders/create">Create an Order</a>)
 * because those constraints are the strictest of the gateways you're likely to
 * integrate; a Stripe adapter would accept a superset.
 */
public final class OrderDomain {

    /** Razorpay: {@code receipt} max length 40, ASCII only. */
    private static final int RECEIPT_MAX_LENGTH = 40;

    /** Razorpay: {@code notes} max 15 key-value pairs. */
    private static final int NOTES_MAX_ENTRIES = 15;

    /** Razorpay: each note key and value max 256 chars. */
    private static final int NOTE_MAX_LENGTH = 256;

    private final long amountInSubunits;
    private final String currency;
    private final String receipt;
    private final Map<String, String> notes;

    public OrderDomain(long amountInSubunits, String currency, String receipt, Map<String, String> notes) {
        if (amountInSubunits <= 0) {
            throw new IllegalArgumentException("amountInSubunits must be positive, got " + amountInSubunits);
        }
        this.amountInSubunits = amountInSubunits;
        this.currency = requireIsoCurrency(currency);
        this.receipt = requireReceipt(receipt);
        this.notes = immutableValidatedNotes(notes);
    }

    public long amountInSubunits() {
        return amountInSubunits;
    }

    public String currency() {
        return currency;
    }

    /** May be {@code null} — Razorpay treats {@code receipt} as optional. */
    public String receipt() {
        return receipt;
    }

    /** Never {@code null}; empty when no notes were supplied. */
    public Map<String, String> notes() {
        return notes;
    }

    private static String requireIsoCurrency(String currency) {
        Objects.requireNonNull(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO-4217 code, got: " + currency);
        }
        return currency.toUpperCase();
    }

    private static String requireReceipt(String receipt) {
        if (receipt == null) {
            return null;
        }
        if (receipt.length() > RECEIPT_MAX_LENGTH) {
            throw new IllegalArgumentException(
                "receipt must be <= " + RECEIPT_MAX_LENGTH + " chars, got " + receipt.length());
        }
        // Razorpay: ASCII only (BAD_REQUEST_ENCODING_VALIDATION_FAILED otherwise).
        for (int i = 0; i < receipt.length(); i++) {
            if (receipt.charAt(i) > 0x7F) {
                throw new IllegalArgumentException(
                    "receipt must contain only ASCII characters, found non-ASCII at index " + i);
            }
        }
        return receipt;
    }

    private static Map<String, String> immutableValidatedNotes(Map<String, String> notes) {
        if (notes == null || notes.isEmpty()) {
            return Collections.emptyMap();
        }
        if (notes.size() > NOTES_MAX_ENTRIES) {
            throw new IllegalArgumentException(
                "notes may contain at most " + NOTES_MAX_ENTRIES + " entries, got " + notes.size());
        }
        Map<String, String> copy = new LinkedHashMap<>(notes.size());
        for (Map.Entry<String, String> e : notes.entrySet()) {
            String key = Objects.requireNonNull(e.getKey(), "note key");
            String value = getValue(e, key);
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static @NonNull String getValue( Map.Entry<String, String> e, String key ) {
        String value = Objects.requireNonNull(e.getValue(), "note value for key '" + key + "'");
        if (key.length() > NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                "note key '" + key + "' exceeds " + NOTE_MAX_LENGTH + " chars");
        }
        if (value.length() > NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                "note value for key '" + key + "' exceeds " + NOTE_MAX_LENGTH + " chars");
        }
        return value;
    }

    @Override
    public String toString() {
        return "OrderDomain{" +
            "amountInSubunits=" + amountInSubunits +
            ", currency='" + currency + '\'' +
            ", receipt='" + receipt + '\'' +
            ", notes=" + notes.size() + " entries" +
            '}';
    }
}
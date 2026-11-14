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

package com.titan.payment.infrastructure.razorpay;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.titan.payment.domain.entity.*;
import com.titan.payment.domain.exception.PaymentGatewayException;
import com.titan.payment.domain.service.PaymentService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Razorpay adapter for {@link PaymentService}.
 * <p>
 * Everything Razorpay-specific lives here: SDK types ({@code Order}, {@code Payment},
 * {@code JSONObject}), status strings, error shapes. The domain only ever sees
 * {@link PaymentOrder}, {@link PaymentDetails} and {@link PaymentGatewayException}.
 *
 * <a href="https://razorpay.com/docs/api">Refer for comprehensive API docs</a>
 */
@Component
public class RazorpayPaymentService implements PaymentService {

    private final RazorpayClient razorpayClient;

    public RazorpayPaymentService( RazorpayClient razorpayClient ) {
        this.razorpayClient = razorpayClient;
    }

    @Override
    public PaymentOrder createOrder( OrderDomain orderDomain ) {
        JSONObject body = new JSONObject();
        body.put("amount", orderDomain.amountInSubunits());
        body.put("currency", orderDomain.currency());
        if (orderDomain.receipt() != null) {
            body.put("receipt", orderDomain.receipt());
        }
        Map<String, String> notes = orderDomain.notes();
        if (notes != null && !notes.isEmpty()) {
            JSONObject notesJson = new JSONObject();
            notes.forEach(notesJson::put);
            body.put("notes", notesJson);
        }

        try {
            Order order = razorpayClient.orders.create(body);
            return toDomainOrder(order);
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("RAZORPAY_CREATE_ORDER_FAILED", e.getMessage(), e);
        }
    }

    @Override
    public PaymentOrder fetchOrder( String orderId ) {
        try {
            Order order = razorpayClient.orders.fetch(orderId);
            return toDomainOrder(order);
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("RAZORPAY_FETCH_ORDER_FAILED", e.getMessage(), e);
        }
    }

    /**
     * Fetches all payments for an order via {@code GET /v1/orders/:id/payments}.
     * The response contains authorised, captured, failed and refunded payments.
     */
    @Override
    public List<PaymentDetails> fetchPaymentsForOrder( String orderId ) {
        try {
            List<Payment> payments = razorpayClient.orders.fetchPayments(orderId);
            List<PaymentDetails> result = new ArrayList<>(payments.size());
            for (Payment payment : payments) {
                result.add(toDomainPayment(payment));
            }
            return Collections.unmodifiableList(result);
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("RAZORPAY_FETCH_ORDER_PAYMENTS_FAILED", e.getMessage(), e);
        }
    }

    /**
     * Fetches a single payment by its own id ({@code pay_*}), via
     * {@code GET /v1/payments/:id}.
     * <p>
     * Note: this is <em>not</em> the same as {@link #fetchPaymentsForOrder(String)}.
     */
    @Override
    public PaymentDetails fetchPayment( String paymentId ) {
        try {
            Payment payment = razorpayClient.payments.fetch(paymentId);
            return toDomainPayment(payment);
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("RAZORPAY_FETCH_PAYMENT_FAILED", e.getMessage(), e);
        }
    }

    @Override
    public PaymentDetails capture( String paymentId, long amountInSubunits, String currency ) {
        if (paymentId == null || paymentId.isBlank())
            throw new IllegalArgumentException("paymentId must not be blank");
        if (amountInSubunits <= 0)
            throw new IllegalArgumentException("amountInSubunits must be positive");

        JSONObject body = new JSONObject();
        body.put("amount", amountInSubunits);
        body.put("currency", currency);

        try {
            Payment payment = razorpayClient.payments.capture(paymentId, body);
            return toDomainPayment(payment);
        } catch (RazorpayException e) {
            throw new PaymentGatewayException(
                    classifyCaptureError(e), e.getMessage(), e);
        }
    }

    private static String classifyCaptureError( RazorpayException e ) {
        String m = e.getMessage() == null ? "" : e.getMessage()
                                                  .toLowerCase();
        if (m.contains("not in authorized state")
                || m.contains("not in captureable")
                || m.contains("already been either captured or voided")
                || m.contains("already been processed")
                || m.contains("already paid"))
            return "RAZORPAY_CAPTURE_CONFLICT";
        if (m.contains("capture amount must be equal")
                || m.contains("amount must be an integer")
                || m.contains("required key is missing")
                || m.contains("cannot be blank"))
            return "RAZORPAY_CAPTURE_INVALID_AMOUNT";
        if (m.contains("currency must be same"))
            return "RAZORPAY_CAPTURE_CURRENCY_MISMATCH";
        if (m.contains("another payment operation is in progress"))
            return "RAZORPAY_CAPTURE_CONCURRENT";
        return "RAZORPAY_CAPTURE_FAILED";
    }

    private PaymentOrder toDomainOrder( Order order ) {
        return new PaymentOrder(
                order.get("id"),
                asLong(order.get("amount")),
                asLong(order.get("amount_paid")),
                asLong(order.get("amount_due")),
                order.get("currency"),
                order.get("receipt"),
                toOrderStatus(order.get("status")),
                (int) asLong(order.get("attempts")),
                toNotes(order.get("notes"))
        );
    }

    private PaymentDetails toDomainPayment( Payment payment ) {
        return new PaymentDetails(
                payment.get("id"),
                payment.get("order_id"),
                asLong(payment.get("amount")),
                asLong(payment.get("amount_refunded")),
                payment.get("currency"),
                toPaymentStatus(payment.get("status")),
                payment.get("method"),
                Boolean.TRUE.equals(payment.get("captured")),
                toNotes(payment.get("notes"))
        );
    }

    private static OrderStatus toOrderStatus( Object raw ) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toString()) {
            case "created" -> OrderStatus.CREATED;
            case "attempted" -> OrderStatus.ATTEMPTED;
            case "paid" -> OrderStatus.PAID;
            default -> throw new PaymentGatewayException(
                    "RAZORPAY_UNKNOWN_ORDER_STATUS", "Unknown order status: " + raw);
        };
    }

    private static PaymentStatus toPaymentStatus( Object raw ) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toString()) {
            case "created" -> PaymentStatus.CREATED;
            case "authorized" -> PaymentStatus.AUTHORIZED;
            case "captured" -> PaymentStatus.CAPTURED;
            case "refunded" -> PaymentStatus.REFUNDED;
            case "failed" -> PaymentStatus.FAILED;
            default -> throw new PaymentGatewayException(
                    "RAZORPAY_UNKNOWN_PAYMENT_STATUS", "Unknown payment status: " + raw);
        };
    }

    /**
     * Razorpay returns {@code notes} as a JSON object when populated, and as
     * an empty array ({@code []}) or {@code null} when empty. Normalise both
     * to an immutable map so the domain never has to care.
     */
    private static Map<String, String> toNotes( Object raw ) {
        if (raw instanceof JSONObject json) {
            Map<String, String> notes = new HashMap<>(json.length());
            for (String key : json.keySet()) {
                notes.put(key, String.valueOf(json.get(key)));
            }
            return Collections.unmodifiableMap(notes);
        }
        if (raw instanceof JSONArray) {
            return Collections.emptyMap();
        }
        return Collections.emptyMap();
    }

    private static long asLong( Object value ) {
        return value == null ? 0L : ( (Number) value ).longValue();
    }
}

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

import com.titan.payment.domain.entity.*;
import com.titan.payment.domain.entity.event.*;
import com.titan.payment.domain.exception.PaymentGatewayException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Translates a Razorpay webhook JSON envelope into a domain {@link WebhookEvent}.
 * <p>
 * The payload shapes come from
 * <a href="https://razorpay.com/docs/api/payments/recurring-payments/webhooks">Razorpay's
 * payments webhook reference</a> and
 * <a href="https://razorpay.com/docs/api/payments/refunds/webhooks">Razorpay's
 * refunds webhook reference</a>.
 * <p>
 * All Razorpay-specific field names live here; nothing downstream sees them.
 * Token, invoice and notification events belong to a different domain
 * (subscriptions) and are not modelled here — they fall through to the
 * {@code default} branch and are rejected.
 */
@Component
public class RazorpayWebhookMapper {

    /**
     * Convenience overload for tests and dry-runs: extracts {@code event} and
     * {@code created_at} from the body. Production callers should use the
     * four-arg overload and pass {@code X-Razorpay-Event-Id} explicitly, since
     * the body has no equivalent.
     */
    public WebhookEvent toDomainEvent( String rawBody ) {
        JSONObject root = new JSONObject(rawBody);
        String eventType = root.getString("event");
        Instant occurredAt = Instant.ofEpochSecond(root.getLong("created_at"));
        return toDomainEvent(eventType, occurredAt, "", root);
    }

    public WebhookEvent toDomainEvent( String eventType, Instant occurredAt,
                                       String gatewayEventId, JSONObject root ) {
        JSONObject payload = root.getJSONObject("payload");
        return switch (eventType) {
            // ---- payments ----
            case "payment.authorized" -> new PaymentAuthorized(
                    gatewayEventId, occurredAt, paymentFrom(payload));
            case "payment.captured" -> new PaymentCaptured(
                    gatewayEventId, occurredAt, paymentFrom(payload));
            case "payment.failed" -> {
                PaymentDetails payment = paymentFrom(payload);
                JSONObject entity = payload.getJSONObject("payment")
                                           .getJSONObject("entity");
                yield new PaymentFailed(
                        gatewayEventId, occurredAt, payment,
                        entity.optString("error_code", null),
                        entity.optString("error_description", null));
            }
            case "order.paid" -> new OrderPaid(
                    gatewayEventId, occurredAt, paymentFrom(payload), orderFrom(payload));
            case "refund.created" -> new RefundCreated(
                    gatewayEventId, occurredAt, refundFrom(payload),
                    paymentFrom(payload), isAutoRefund(payload));
            case "refund.processed" -> new RefundProcessed(
                    gatewayEventId, occurredAt, refundFrom(payload),
                    paymentFrom(payload), isAutoRefund(payload));
            case "refund.failed" -> new RefundFailed(
                    gatewayEventId, occurredAt, refundFrom(payload),
                    paymentFrom(payload), isAutoRefund(payload));
            case "refund.speed_changed" -> new RefundSpeedChanged(
                    gatewayEventId, occurredAt, refundFrom(payload),
                    paymentFrom(payload), isAutoRefund(payload));

            default -> throw new PaymentGatewayException(
                    "RAZORPAY_WEBHOOK_UNSUPPORTED_EVENT",
                    "Unsupported webhook event: " + eventType + " (eventId=" + gatewayEventId + ")");
        };
    }

    // Same shapes as GET /v1/payments/:id and GET /v1/orders/:id — reused here
    // so the webhook path and the polling path produce identical domain objects.

    private static PaymentDetails paymentFrom( JSONObject payload ) {
        JSONObject e = requireEntity(payload, "payment");
        return new PaymentDetails(
                e.getString("id"),
                e.optString("order_id", null),
                e.getLong("amount"),
                e.optLong("amount_refunded", 0L),
                e.getString("currency"),
                toPaymentStatus(e.getString("status")),
                e.optString("method", null),
                e.optBoolean("captured", false),
                toNotes(e.opt("notes"))
        );
    }

    private static PaymentOrder orderFrom( JSONObject payload ) {
        JSONObject e = requireEntity(payload, "order");
        return new PaymentOrder(
                e.getString("id"),
                e.getLong("amount"),
                e.optLong("amount_paid", 0L),
                e.optLong("amount_due", 0L),
                e.getString("currency"),
                e.optString("receipt", null),
                toOrderStatus(e.getString("status")),
                e.optInt("attempts", 0),
                toNotes(e.opt("notes"))
        );
    }

    private static RefundDetails refundFrom( JSONObject payload ) {
        JSONObject e = requireEntity(payload, "refund");
        JSONObject acquirer = e.optJSONObject("acquirer_data");
        return new RefundDetails(
                e.getString("id"),
                e.getString("payment_id"),
                e.getLong("amount"),
                e.getString("currency"),
                toRefundStatus(e.getString("status")),
                e.optString("speed_processed", null),
                e.optString("speed_requested", null),
                e.optString("receipt", null),
                toNotes(e.opt("notes")),
                acquirer == null ? null : acquirer.optString("arn", null),
                e.optString("batch_id", null)
        );
    }

    /**
     * Returns {@code payload[entityName].entity}, throwing a
     * {@link PaymentGatewayException} (not a raw {@code JSONException}) if the
     * entity is missing, so the controller can map it to a client-visible outcome
     * rather than a 500.
     */
    private static JSONObject requireEntity( JSONObject payload, String entityName ) {
        JSONObject wrapper = payload.optJSONObject(entityName);
        if (wrapper == null) {
            throw new PaymentGatewayException(
                    "RAZORPAY_WEBHOOK_MISSING_ENTITY",
                    "Webhook payload has no '" + entityName + "' entity");
        }
        JSONObject entity = wrapper.optJSONObject("entity");
        if (entity == null) {
            throw new PaymentGatewayException(
                    "RAZORPAY_WEBHOOK_MISSING_ENTITY",
                    "Webhook payload has no '" + entityName + ".entity'");
        }
        return entity;
    }

    /**
     * Per Razorpay's refund webhook docs: auto-refunds have
     * {@code payload.payment.entity.captured == false}. Default to {@code false}
     * (i.e. treat as a normal business refund) if the field is missing, so a
     * customer notification is never silently suppressed.
     */
    private static boolean isAutoRefund( JSONObject payload ) {
        return !requireEntity(payload, "payment").optBoolean("captured", true);
    }

    private static PaymentStatus toPaymentStatus( String raw ) {
        return switch (raw) {
            case "created" -> PaymentStatus.CREATED;
            case "authorized" -> PaymentStatus.AUTHORIZED;
            case "captured" -> PaymentStatus.CAPTURED;
            case "refunded" -> PaymentStatus.REFUNDED;
            case "failed" -> PaymentStatus.FAILED;
            default -> throw new PaymentGatewayException(
                    "RAZORPAY_UNKNOWN_PAYMENT_STATUS", "Unknown payment status: " + raw);
        };
    }

    private static OrderStatus toOrderStatus( String raw ) {
        return switch (raw) {
            case "created" -> OrderStatus.CREATED;
            case "attempted" -> OrderStatus.ATTEMPTED;
            case "paid" -> OrderStatus.PAID;
            default -> throw new PaymentGatewayException(
                    "RAZORPAY_UNKNOWN_ORDER_STATUS", "Unknown order status: " + raw);
        };
    }

    private static RefundStatus toRefundStatus( String raw ) {
        return switch (raw) {
            case "pending" -> RefundStatus.PENDING;
            case "processed" -> RefundStatus.PROCESSED;
            case "failed" -> RefundStatus.FAILED;
            default -> throw new PaymentGatewayException(
                    "RAZORPAY_UNKNOWN_REFUND_STATUS", "Unknown refund status: " + raw);
        };
    }

    /**
     * Razorpay returns {@code notes} as a JSON object when populated, and as
     * an empty array ({@code []}) or {@code null} when empty. Normalise both
     * to an immutable map. Immutability is enforced here (at the wire boundary)
     * so domain entities don't need to re-wrap.
     */
    private static Map<String, String> toNotes( Object raw ) {
        if (raw instanceof JSONObject json) {
            Map<String, String> notes = new HashMap<>(json.length());
            for (String key : json.keySet()) {
                notes.put(key, String.valueOf(json.get(key)));
            }
            return Collections.unmodifiableMap(notes);
        }
        return Collections.emptyMap(); // covers [] and null
    }
}
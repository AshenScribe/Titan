package com.titan.payment.application.service;

import com.titan.payment.application.kafka.event.OrderCreatedEvent;
import com.titan.payment.domain.entity.OrderDomain;
import com.titan.payment.domain.entity.PaymentOrder;
import com.titan.payment.domain.port.PaymentAttemptPort;
import com.titan.payment.domain.service.PaymentService;
import com.titan.payment.infrastructure.persistence.exception.DuplicatePaymentAttemptException;
import com.titan.payment.infrastructure.persistence.model.PaymentAttemptEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates a {@code payment_attempts} row for an accepted order.
 * <p>
 * Sits between the Kafka consumer and the Razorpay adapter. Its job is to
 * satisfy two invariants at once:
 * <ul>
 *   <li><b>An accepted order is never lost</b> — either the attempt row is
 *       written, or the exception propagates so the Kafka error handler retries.</li>
 *   <li><b>A client retry never double-charges</b> — the {@code (order_id,
 *       idempotency_key)} unique constraint plus Razorpay's {@code receipt}
 *       idempotency key together guarantee that redelivery of
 *       {@code order.created} produces at most one gateway order.</li>
 * </ul>
 *
 * <h3>Why the Razorpay call is outside {@code @Transactional}</h3>
 * <p>
 * Holding a database transaction open across an HTTP call to Razorpay
 * exhausts the connection pool under load, and the transaction is doomed
 * anyway: if Razorpay succeeds but the DB commit fails, the external effect
 * has already happened. The correct pattern is:
 * <ol>
 *   <li>Check the idempotency key (cheap, outside any transaction).</li>
 *   <li>If not found, call Razorpay with {@code receipt = idempotencyKey}
 *       (so a retry after a crash is idempotent <em>at Razorpay</em>).</li>
 *   <li>Insert the row. If a concurrent delivery raced us, the unique
 *       constraint catches it and we swallow {@link DuplicatePaymentAttemptException}.</li>
 * </ol>
 * <p>
 * A crash between (2) and (3) leaves an orphan gateway order with no local
 * row. The customer never sees it, so it's harmless, and Razorpay won't create
 * a second one on retry because the receipt is the same. A
 * reconciliation job can sweep orphans by querying Razorpay for orders by
 * receipt if that ever matters.
 */
@Service
public class CreatePaymentAttemptService {

    private static final Logger log = LoggerFactory.getLogger(CreatePaymentAttemptService.class);

    private final PaymentAttemptPort attempts;
    private final PaymentService paymentService;

    public CreatePaymentAttemptService( PaymentAttemptPort attempts,
                                        PaymentService paymentService ) {
        this.attempts = attempts;
        this.paymentService = paymentService;
    }

    /**
     * No {@code @Transactional}: the Razorpay call must not hold a DB connection.
     * The insert itself is atomic by virtue of the unique constraint.
     */
    public void handle( OrderCreatedEvent event ) {
        Optional<PaymentAttemptEntity> existing =
                attempts.findByOrderIdAndIdempotencyKey(event.orderId(), event.idempotencyKey());
        if (existing.isPresent()) {
            log.debug("order.created duplicate skipped orderId={} eventId={}",
                      event.orderId(), event.eventId());
            return;
        }

        // Razorpay treats `receipt` as an idempotency key; a second createOrder
        // with the same receipt returns 400 Duplicate request. Falling back to
        // the idempotency key guarantees that property even if order-service
        // does not supply a receipt.
        String receipt = event.receipt() != null ? event.receipt() : event.idempotencyKey();

        OrderDomain order = new OrderDomain(
                event.amountInSubunits(), event.currency(), receipt, toNotes(event.notes()));
        PaymentOrder gatewayOrder = paymentService.createOrder(order);

        PaymentAttemptEntity entity = new PaymentAttemptEntity(
                UUID.randomUUID(),
                event.orderId(),
                gatewayOrder.id(),
                event.amountInSubunits(),
                event.currency(),
                "CREATED",
                event.idempotencyKey(),
                "v1",
                receipt,
                event.notes(),
                0L,
                OffsetDateTime.now(),
                OffsetDateTime.now());

        try {
            attempts.insert(entity);
        } catch (DuplicatePaymentAttemptException e) {
            // Concurrent delivery (two consumer threads picked up the same offset
            // during a rebalance). The other thread won; our insert is a no-op.
            log.info("order.created concurrent insert orderId={} key={} — treating as success",
                     event.orderId(), event.idempotencyKey());
        }
    }

    /**
     * Converts the free-form event notes to the string map the domain expects.
     */
    private static Map<String, String> toNotes( Map<String, Object> raw ) {
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, String> out = new java.util.LinkedHashMap<>(raw.size());
        raw.forEach(( k, v ) -> out.put(k, v == null ? null : v.toString()));
        return out;
    }
}
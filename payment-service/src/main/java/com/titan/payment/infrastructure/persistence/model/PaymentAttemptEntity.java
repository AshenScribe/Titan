package com.titan.payment.infrastructure.persistence.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record PaymentAttemptEntity(
        UUID id,
        UUID orderId,
        String gatewayOrderId,
        long amount,
        String currency,
        String status,
        String idempotencyKey,
        String requestHash,
        String receipt,
        Map<String, Object> notes,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
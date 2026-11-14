package com.titan.payment.infrastructure.persistence.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record PaymentAttemptEventEntity(
        UUID id,
        UUID paymentAttemptId,
        String fromStatus,
        String toStatus,
        String source,
        String webhookEventId,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {}
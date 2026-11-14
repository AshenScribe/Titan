package com.titan.payment.infrastructure.persistence.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record GatewayPaymentEntity(
        UUID id,
        UUID paymentAttemptId,
        String gatewayPaymentId,
        long amount,
        long amountRefunded,
        String currency,
        String status,
        String method,
        boolean captured,
        Map<String, Object> notes,
        OffsetDateTime gatewayCreatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version
) {}
package com.titan.payment.infrastructure.persistence.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record RefundEntity(
        UUID id,
        UUID gatewayPaymentId,
        String gatewayRefundId,
        long amount,
        String currency,
        String status,
        String speedRequested,
        String speedProcessed,
        boolean autoRefund,
        Map<String, Object> notes,
        String acquirerReferenceNumber,
        String batchId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version
) {}
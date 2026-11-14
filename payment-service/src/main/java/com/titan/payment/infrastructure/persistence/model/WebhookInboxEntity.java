package com.titan.payment.infrastructure.persistence.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record WebhookInboxEntity(
        UUID id,
        String gatewayEventId,
        String eventType,
        Map<String, Object> payload,
        OffsetDateTime occurredAt,
        OffsetDateTime receivedAt,
        OffsetDateTime processedAt,
        String status,
        int processingAttempts,
        String lastError
) {}
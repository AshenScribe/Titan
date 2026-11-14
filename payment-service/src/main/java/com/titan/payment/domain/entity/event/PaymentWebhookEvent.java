package com.titan.payment.domain.entity.event;

public sealed interface PaymentWebhookEvent extends WebhookEvent
    permits PaymentAuthorized, PaymentCaptured, PaymentFailed, OrderPaid {}
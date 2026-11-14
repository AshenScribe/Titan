-- -----------------------------------------------------------------------------
--  MIT License
--
--  Copyright (c) 2026 Rishabh Saraswat
--
--  Permission is hereby granted, free of charge, to any person obtaining a copy
--  of this software and associated documentation files (the "Software"), to deal
--  in the Software without restriction, including without limitation the rights
--  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
--  copies of the Software, and to permit persons to whom the Software is
--  furnished to do so, subject to the following conditions:
--
--  The above copyright notice and this permission notice shall be included in all
--  copies or substantial portions of the Software.
--
--  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
--  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
--  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
--  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
--  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
--  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
--  SOFTWARE.
--
-- -----------------------------------------------------------------------------

CREATE TABLE payment_attempts
(
    id               UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    order_id         UUID         NOT NULL,
    gateway_order_id VARCHAR(64) UNIQUE,
    amount           BIGINT       NOT NULL CHECK (amount > 0),
    currency         CHAR(3)      NOT NULL,
    status           VARCHAR(24)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN (
                          'PENDING',
                          'CREATED',
                          'AUTHORIZED',
                          'CAPTURED',
                          'FAILED',
                          'CANCELLED'
            )),
    idempotency_key  VARCHAR(128) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    receipt          VARCHAR(40),
    notes            JSONB        NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(notes) = 'object'),
    version          BIGINT       NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payment_attempt_idempotency
        UNIQUE (order_id, idempotency_key)
);

CREATE INDEX idx_payment_attempts_order
    ON payment_attempts (order_id, created_at DESC);

CREATE INDEX idx_payment_attempts_status_created
    ON payment_attempts (created_at) WHERE status IN ('PENDING', 'CREATED', 'AUTHORIZED');

CREATE TABLE gateway_payments
(
    id                 UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    payment_attempt_id UUID        NOT NULL
        REFERENCES payment_attempts (id) ON DELETE RESTRICT,
    gateway_payment_id VARCHAR(64) NOT NULL UNIQUE,
    amount             BIGINT      NOT NULL CHECK (amount > 0),
    amount_refunded    BIGINT      NOT NULL DEFAULT 0
        CHECK (amount_refunded >= 0),
    currency           CHAR(3)     NOT NULL,
    status             VARCHAR(24) NOT NULL
        CHECK (status IN (
                          'CREATED',
                          'AUTHORIZED',
                          'CAPTURED',
                          'REFUNDED',
                          'FAILED'
            )),
    method             VARCHAR(32),
    captured           BOOLEAN     NOT NULL DEFAULT FALSE,
    notes              JSONB       NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(notes) = 'object'),
    gateway_created_at TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version            BIGINT      NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT chk_refunded_amount
        CHECK (amount_refunded <= amount)
);

CREATE INDEX idx_gateway_payments_attempt
    ON gateway_payments (payment_attempt_id, created_at DESC);

CREATE INDEX idx_gateway_payments_status
    ON gateway_payments (status, updated_at);

CREATE TABLE refunds
(
    id                        UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    gateway_payment_id        UUID        NOT NULL
        REFERENCES gateway_payments (id) ON DELETE RESTRICT,
    gateway_refund_id         VARCHAR(64) UNIQUE,
    amount                    BIGINT      NOT NULL CHECK (amount > 0),
    currency                  CHAR(3)     NOT NULL,
    status                    VARCHAR(24) NOT NULL
        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    speed_requested           VARCHAR(32),
    speed_processed           VARCHAR(32),
    auto_refund               BOOLEAN     NOT NULL DEFAULT FALSE,
    notes                     JSONB       NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(notes) = 'object'),
    acquirer_reference_number VARCHAR(128),
    batch_id                  VARCHAR(128),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                   BIGINT      NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE INDEX idx_refunds_payment
    ON refunds (gateway_payment_id, created_at DESC);

CREATE INDEX idx_refunds_pending
    ON refunds (created_at) WHERE status = 'PENDING';

CREATE TABLE webhook_inbox
(
    id                  UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    gateway_event_id    VARCHAR(128) NOT NULL UNIQUE,
    event_type          VARCHAR(128) NOT NULL,
    payload             JSONB        NOT NULL,
    occurred_at         TIMESTAMPTZ,
    received_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMPTZ,
    status              VARCHAR(16)  NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN (
                          'RECEIVED',
                          'PROCESSING',
                          'PROCESSED',
                          'FAILED'
            )),
    processing_attempts INTEGER      NOT NULL DEFAULT 0
        CHECK (processing_attempts >= 0),
    last_error          TEXT
);

CREATE INDEX idx_webhook_inbox_unprocessed
    ON webhook_inbox (received_at) WHERE status IN ('RECEIVED', 'FAILED');

CREATE TABLE payment_attempt_events
(
    id                 UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    payment_attempt_id UUID        NOT NULL
        REFERENCES payment_attempts (id) ON DELETE RESTRICT,
    from_status        VARCHAR(24),
    to_status          VARCHAR(24) NOT NULL,
    source             VARCHAR(24) NOT NULL
        CHECK (source IN (
                          'API',
                          'WEBHOOK',
                          'RECONCILIATION',
                          'SYSTEM'
            )),
    webhook_event_id   VARCHAR(128),
    metadata           JSONB       NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(metadata) = 'object'),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_attempt_events_attempt
    ON payment_attempt_events (payment_attempt_id, created_at);

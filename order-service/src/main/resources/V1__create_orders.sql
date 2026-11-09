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
CREATE TYPE order_status AS ENUM (
    'pending',
    'payment_processing',
    'paid',
    'fulfillment',
    'shipped',
    'delivered',
    'cancelled',
    'refunded'
    );

CREATE TABLE orders
(
    id                    UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    user_id               UUID           NOT NULL,
    status                order_status   NOT NULL DEFAULT 'pending',
    currency VARCHAR NOT NULL DEFAULT 'INR'
        CHECK (currency ~ '^[A-Z]{3}$'),
    subtotal_amount       NUMERIC(12, 2) NOT NULL
        CHECK (subtotal_amount >= 0),
    tax_amount            NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (tax_amount >= 0),
    shipping_amount       NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (shipping_amount >= 0),
    discount_amount       NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (discount_amount >= 0),
    total_amount          NUMERIC(14, 2)
        GENERATED ALWAYS AS (
            subtotal_amount
                + tax_amount
                + shipping_amount
                - discount_amount
            ) STORED
        CHECK (total_amount >= 0),
    shipping_address      JSONB          NOT NULL
        CHECK (jsonb_typeof(shipping_address) = 'object'),
    billing_address       JSONB
        CHECK (
            billing_address IS NULL
                OR jsonb_typeof(billing_address) = 'object'
            ),
    customer_note         VARCHAR(1000),
    version               BIGINT         NOT NULL DEFAULT 0
        CHECK (version >= 0),
    inventory_reserved_at TIMESTAMPTZ,
    paid_at               TIMESTAMPTZ,
    confirmed_at          TIMESTAMPTZ,
    shipped_at            TIMESTAMPTZ,
    delivered_at          TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    refunded_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT chk_order_total_non_negative
        CHECK (total_amount >= 0)
);

CREATE TABLE order_items
(
    id           UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    order_id     UUID           NOT NULL
        REFERENCES orders (id) ON DELETE CASCADE,
    product_id   UUID           NOT NULL,
    variant_id   UUID           NOT NULL,
    product_name VARCHAR(255)   NOT NULL,
    variant_name VARCHAR(255),
    sku          VARCHAR(100)   NOT NULL,
    attributes   JSONB          NOT NULL DEFAULT '{}'
        CHECK (jsonb_typeof(attributes) = 'object'),
    quantity     INTEGER        NOT NULL
        CHECK (quantity > 0),
    unit_price   NUMERIC(12, 2) NOT NULL
        CHECK (unit_price >= 0),
    position     SMALLINT       NOT NULL
        CHECK (position >= 0),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_order_item_variant
        UNIQUE (order_id, variant_id),
    CONSTRAINT uq_order_item_position
        UNIQUE (order_id, position)
);

-- before placing an order, order's hash must be checked in this table, each row must be cleared after expired_at
CREATE TABLE order_idempotency
(
    user_id         UUID         NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash    BYTEA        NOT NULL
        CHECK (octet_length(request_hash) = 32),
    order_id        UUID         NOT NULL UNIQUE
        REFERENCES orders (id) ON DELETE RESTRICT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, idempotency_key)
);

CREATE INDEX idx_orders_user_created
    ON orders (user_id, created_at DESC, id DESC);

CREATE INDEX idx_orders_pending
    ON orders (created_at, id)
    WHERE status IN ('pending', 'payment_processing');

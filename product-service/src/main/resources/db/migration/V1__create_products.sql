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

CREATE TABLE brands
(
    id          UUID PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    slug        VARCHAR(180) NOT NULL,
    description TEXT,
    website_url TEXT,
    logo_url    TEXT,
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb
                CHECK (jsonb_typeof(metadata) = 'object'),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT brands_name_not_empty
        CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE UNIQUE INDEX uq_brands_slug
    ON brands (LOWER(slug));

CREATE TABLE categories
(
    id          UUID PRIMARY KEY,
    parent_id   UUID
        REFERENCES categories (id)
            ON DELETE RESTRICT,
    name        VARCHAR(150) NOT NULL,
    slug        VARCHAR(180) NOT NULL,
    description TEXT,
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb
                CHECK (jsonb_typeof(metadata) = 'object'),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT categories_name_not_empty
        CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT categories_no_self_parent
        CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX uq_categories_slug
    ON categories (LOWER(slug));

CREATE INDEX idx_categories_parent
    ON categories (parent_id);


CREATE TABLE products
(
    id           UUID PRIMARY KEY,
    brand_id     UUID
        REFERENCES brands (id)
            ON DELETE RESTRICT,
    name         VARCHAR(255) NOT NULL,
    slug         VARCHAR(280) NOT NULL,
    description  TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN (
                          'DRAFT',
                          'ACTIVE',
                          'INACTIVE'
            )),
    metadata     JSONB        NOT NULL DEFAULT '{}'::jsonb
                 CHECK (jsonb_typeof(metadata) = 'object'),
    version      BIGINT       NOT NULL DEFAULT 0
        CHECK (version >= 0),
    published_at TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT products_name_not_empty
        CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT products_active_requires_publication
        CHECK (
            status <> 'ACTIVE'
                OR (
                published_at IS NOT NULL
                    AND deleted_at IS NULL
                )
            ),
    CONSTRAINT products_deleted_not_active
        CHECK (
            deleted_at IS NULL
                OR status <> 'ACTIVE'
            )
);

CREATE UNIQUE INDEX uq_products_slug
    ON products (LOWER(slug)) WHERE deleted_at IS NULL;

CREATE INDEX idx_products_active_created
    ON products (created_at DESC, id) WHERE status = 'ACTIVE'
      AND deleted_at IS NULL;

CREATE INDEX idx_products_brand
    ON products (brand_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_products_metadata_gin
    ON products USING GIN (metadata jsonb_path_ops);

CREATE TABLE product_categories
(
    product_id  UUID        NOT NULL
        REFERENCES products (id)
            ON DELETE CASCADE,
    category_id UUID        NOT NULL
        REFERENCES categories (id)
            ON DELETE RESTRICT,
    is_primary  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (product_id, category_id)
);

CREATE UNIQUE INDEX uq_product_primary_category
    ON product_categories (product_id) WHERE is_primary = TRUE;

CREATE INDEX idx_product_categories_category
    ON product_categories (category_id, product_id);

CREATE TABLE product_variants
(
    id               UUID PRIMARY KEY,
    product_id       UUID           NOT NULL
        REFERENCES products (id)
            ON DELETE CASCADE,
    sku              VARCHAR(100)   NOT NULL,
    barcode          VARCHAR(100),
    name             VARCHAR(255),
    price            NUMERIC(12, 2) NOT NULL
        CHECK (price >= 0),
    compare_at_price NUMERIC(12, 2)
        CHECK (
            compare_at_price IS NULL
                OR compare_at_price >= price
            ),
    currency         CHAR(3)        NOT NULL DEFAULT 'INR'
        CHECK (currency ~ '^[A-Z]{3}$'
) ,
    attributes       JSONB NOT NULL DEFAULT '{}'::jsonb
                     CHECK (jsonb_typeof(attributes) = 'object'),
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE', 'INACTIVE')),
    version          BIGINT NOT NULL DEFAULT 0
                     CHECK (version >= 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_product_variants_sku
    ON product_variants (LOWER(sku));

CREATE UNIQUE INDEX uq_product_variants_barcode
    ON product_variants (barcode) WHERE barcode IS NOT NULL;

CREATE INDEX idx_product_variants_product
    ON product_variants (product_id);

CREATE INDEX idx_product_variants_active_price
    ON product_variants (product_id, price) WHERE status = 'ACTIVE';

CREATE INDEX idx_product_variants_attributes_gin
    ON product_variants USING GIN (attributes jsonb_path_ops);

CREATE TABLE product_media
(
    id          UUID PRIMARY KEY,
    product_id  UUID        NOT NULL
        REFERENCES products (id)
            ON DELETE CASCADE,
    media_type  VARCHAR(20) NOT NULL
        CHECK (media_type IN ('IMAGE', 'VIDEO')),
    storage_key TEXT        NOT NULL,
    alt_text    VARCHAR(500),
    position    INTEGER     NOT NULL DEFAULT 0
        CHECK (position >= 0),
    is_primary  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_media_position
        UNIQUE (product_id, position)
            DEFERRABLE INITIALLY DEFERRED
);

CREATE UNIQUE INDEX uq_product_primary_media
    ON product_media (product_id) WHERE is_primary = TRUE;

CREATE INDEX idx_product_media_product
    ON product_media (product_id, position);


CREATE
OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at
:= clock_timestamp();
RETURN NEW;
END;
$$;

CREATE TRIGGER trg_brands_updated_at
    BEFORE UPDATE
    ON brands
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_categories_updated_at
    BEFORE UPDATE
    ON categories
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE
    ON products
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_product_variants_updated_at
    BEFORE UPDATE
    ON product_variants
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();


CREATE
OR REPLACE FUNCTION prevent_category_cycle()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
creates_cycle BOOLEAN;
BEGIN
    PERFORM
pg_advisory_xact_lock(73190421);

    IF
NEW.parent_id IS NULL THEN
        RETURN NEW;
END IF;

    IF
NEW.parent_id = NEW.id THEN
        RAISE EXCEPTION
            'A category cannot be its own parent';
END IF;

WITH RECURSIVE descendants(id) AS
                   (SELECT id
                    FROM categories
                    WHERE parent_id = NEW.id

                    UNION

                    SELECT c.id
                    FROM categories c
                             JOIN descendants d
                                  ON c.parent_id = d.id)
SELECT EXISTS (SELECT 1
               FROM descendants
               WHERE id = NEW.parent_id)
INTO creates_cycle;

IF
creates_cycle THEN
        RAISE EXCEPTION
            'Category hierarchy cycle detected for category %',
            NEW.id;
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER trg_prevent_category_cycle
    BEFORE INSERT OR
UPDATE OF parent_id
ON categories
    FOR EACH ROW
    EXECUTE FUNCTION prevent_category_cycle();

CREATE
OR REPLACE FUNCTION assert_active_product_has_variant(
    target_product_id UUID
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    IF
EXISTS (
        SELECT 1
        FROM products p
        WHERE p.id = target_product_id
          AND p.status = 'ACTIVE'
          AND p.deleted_at IS NULL
    )
    AND NOT EXISTS (
        SELECT 1
        FROM product_variants v
        WHERE v.product_id = target_product_id
          AND v.status = 'ACTIVE'
    )
    THEN
        RAISE EXCEPTION
            'Active product % must have at least one active variant',
            target_product_id
            USING ERRCODE = '23514';
END IF;
END;
$$;


CREATE
OR REPLACE FUNCTION check_active_product_variant_constraint()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF
TG_TABLE_NAME = 'products' THEN

        PERFORM assert_active_product_has_variant(NEW.id);

    ELSIF
TG_OP = 'DELETE' THEN

        PERFORM assert_active_product_has_variant(OLD.product_id);

    ELSIF
TG_OP = 'UPDATE' THEN

        PERFORM assert_active_product_has_variant(OLD.product_id);

        IF
NEW.product_id IS DISTINCT FROM OLD.product_id THEN
            PERFORM assert_active_product_has_variant(NEW.product_id);
END IF;

ELSE

        PERFORM assert_active_product_has_variant(NEW.product_id);

END IF;

RETURN NULL;
END;
$$;


CREATE
CONSTRAINT TRIGGER trg_products_require_active_variant
    AFTER INSERT OR
UPDATE
    ON products
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_active_product_variant_constraint();


CREATE
CONSTRAINT TRIGGER trg_variants_require_active_product_variant
    AFTER INSERT OR
UPDATE OR
DELETE
ON product_variants
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_active_product_variant_constraint();

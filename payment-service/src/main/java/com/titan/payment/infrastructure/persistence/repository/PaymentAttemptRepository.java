// ------------------------------------------------------------------------------
// MIT License
//
// Copyright (c) 2026 Rishabh Saraswat
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
// ------------------------------------------------------------------------------

package com.titan.payment.infrastructure.persistence.repository;

import com.titan.payment.domain.entity.enums.PaymentAttemptStatus;
import com.titan.payment.domain.port.PaymentAttemptPort;
import com.titan.payment.infrastructure.persistence.model.PaymentAttemptEntity;
import com.titan.payment.infrastructure.persistence.util.JdbcSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
public class PaymentAttemptRepository implements PaymentAttemptPort {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String COLUMNS =
            "id, order_id, gateway_order_id, amount, currency, status, "
                    + "idempotency_key, request_hash, receipt, notes, version, "
                    + "created_at, updated_at";

    private static final String TABLE_NAME = "payment_attempts";

    private static final String INSERT_STATEMENT =
            """
            INSERT INTO %s (
                id, order_id, gateway_order_id, amount, currency, status,
                idempotency_key, request_hash, receipt, notes, version,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(TABLE_NAME);

    private static final String FIND_BY_ID =
            "SELECT %s FROM %s WHERE id = ?".formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_ORDER_AND_IDEMPOTENCY =
            "SELECT %s FROM %s WHERE order_id = ? AND idempotency_key = ?"
                    .formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_ORDER_ID =
            """
            SELECT %s FROM %s WHERE order_id = ?
            ORDER BY created_at DESC, id DESC
            """.formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_STATUS =
            """
            SELECT %s FROM %s WHERE status = ?
            ORDER BY created_at DESC, id DESC
            """.formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_STALE =
            """
            SELECT %s FROM %s
            WHERE status IN ('PENDING', 'CREATED', 'AUTHORIZED')
              AND created_at < ?
            ORDER BY created_at
            LIMIT ?
            """.formatted(COLUMNS, TABLE_NAME);

    private static final String UPDATE =
            """
            UPDATE %s
            SET gateway_order_id = ?,
                amount           = ?,
                currency         = ?,
                status           = ?,
                request_hash     = ?,
                receipt          = ?,
                notes            = ?,
                version          = version + 1,
                updated_at       = ?
            WHERE id = ? AND version = ?
            """.formatted(TABLE_NAME);

    private final RowMapper<PaymentAttemptEntity> rowMapper = this::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public PaymentAttemptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(PaymentAttemptEntity entity) {
        execute(() -> jdbcTemplate.update(
                INSERT_STATEMENT,
                statement -> bindInsert(statement, entity)));
    }

    /** Optimistic-locking update. Returns 1 if applied, 0 if version drifted. */
    public int update(PaymentAttemptEntity entity) {
        return execute(() -> jdbcTemplate.update(
                UPDATE,
                entity.gatewayOrderId(),
                entity.amount(),
                entity.currency(),
                entity.status(),
                entity.requestHash(),
                entity.receipt(),
                JdbcSupport.asParam(entity.notes()),
                entity.updatedAt(),
                entity.id(),
                entity.version()));
    }

    public Optional<PaymentAttemptEntity> findById(UUID id) {
        return execute(() -> jdbcTemplate.query(FIND_BY_ID, rowMapper, id).stream().findFirst());
    }

    public Optional<PaymentAttemptEntity> findByOrderIdAndIdempotencyKey(UUID orderId, String key) {
        return execute(() ->
                jdbcTemplate.query(FIND_BY_ORDER_AND_IDEMPOTENCY, rowMapper, orderId, key)
                        .stream().findFirst());
    }

    public List<PaymentAttemptEntity> findByOrderId(UUID orderId) {
        return execute(() -> jdbcTemplate.query(FIND_BY_ORDER_ID, rowMapper, orderId));
    }

    public List<PaymentAttemptEntity> findByStatus(PaymentAttemptStatus status, int limit) {
        Objects.requireNonNull(status, "Status must not be null");
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        return execute(() -> jdbcTemplate.query(FIND_BY_STATUS, rowMapper, status.name()));
    }

    public List<PaymentAttemptEntity> findStaleBefore(OffsetDateTime cutoff, int limit) {
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        return execute(() -> jdbcTemplate.query(FIND_STALE, rowMapper, cutoff, limit));
    }

    private void bindInsert(PreparedStatement statement, PaymentAttemptEntity entity)
            throws SQLException {
        int index = 1;
        statement.setObject(index++, entity.id());
        statement.setObject(index++, entity.orderId());
        statement.setString(index++, entity.gatewayOrderId());
        statement.setLong(index++, entity.amount());
        statement.setString(index++, entity.currency());
        statement.setString(index++, entity.status());
        statement.setString(index++, entity.idempotencyKey());
        statement.setString(index++, entity.requestHash());
        statement.setString(index++, entity.receipt());
        JdbcSupport.bind(statement, index++, entity.notes());
        statement.setLong(index++, entity.version());
        statement.setObject(index++, entity.createdAt());
        statement.setObject(index, entity.updatedAt());
    }

    private PaymentAttemptEntity mapRow(java.sql.ResultSet rs, int rowNumber) throws SQLException {
        return new PaymentAttemptEntity(
                JdbcSupport.readUuid(rs, "id"),
                JdbcSupport.readUuid(rs, "order_id"),
                rs.getString("gateway_order_id"),
                rs.getLong("amount"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                rs.getString("receipt"),
                JdbcSupport.readJsonb(rs, "notes"),
                rs.getLong("version"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException exception) {
            throw PaymentPersistenceExceptionTranslator.translate(exception);
        }
    }
}
//------------------------------------------------------------------------------
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
//------------------------------------------------------------------------------

package com.titan.payment.infrastructure.persistence.repository;

import com.titan.payment.infrastructure.persistence.model.RefundEntity;
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
public class RefundRepository {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String COLUMNS =
            "id, gateway_payment_id, gateway_refund_id, amount, currency, status, "
                    + "speed_requested, speed_processed, auto_refund, notes, "
                    + "acquirer_reference_number, batch_id, created_at, updated_at, version";

    private static final String TABLE_NAME = "refunds";

    private static final String INSERT_STATEMENT =
            """
            INSERT INTO %s (
                id, gateway_payment_id, gateway_refund_id, amount, currency, status,
                speed_requested, speed_processed, auto_refund, notes,
                acquirer_reference_number, batch_id, created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(TABLE_NAME);

    private static final String FIND_BY_ID =
            "SELECT %s FROM %s WHERE id = ?".formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_GATEWAY_REFUND_ID =
            "SELECT %s FROM %s WHERE gateway_refund_id = ?".formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_GATEWAY_PAYMENT =
            """
            SELECT %s FROM %s WHERE gateway_payment_id = ?
            ORDER BY created_at DESC, id DESC
            """.formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_PENDING =
            """
            SELECT %s FROM %s
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT ?
            """.formatted(COLUMNS, TABLE_NAME);

    private static final String UPDATE =
            """
            UPDATE %s
            SET gateway_refund_id         = ?,
                status                    = ?,
                speed_processed           = ?,
                notes                     = ?,
                acquirer_reference_number = ?,
                batch_id                  = ?,
                version                   = version + 1,
                updated_at                = ?
            WHERE id = ? AND version = ?
            """.formatted(TABLE_NAME);

    private final RowMapper<RefundEntity> rowMapper = this::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public RefundRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(RefundEntity entity) {
        execute(() -> jdbcTemplate.update(
                INSERT_STATEMENT,
                statement -> bindInsert(statement, entity)));
    }

    public int update(RefundEntity entity) {
        return execute(() -> jdbcTemplate.update(
                UPDATE,
                entity.gatewayRefundId(),
                entity.status(),
                entity.speedProcessed(),
                JdbcSupport.asParam(entity.notes()),
                entity.acquirerReferenceNumber(),
                entity.batchId(),
                entity.updatedAt(),
                entity.id(),
                entity.version()));
    }

    public Optional<RefundEntity> findById(UUID id) {
        return execute(() -> jdbcTemplate.query(FIND_BY_ID, rowMapper, id).stream().findFirst());
    }

    public Optional<RefundEntity> findByGatewayRefundId(String gatewayRefundId) {
        Objects.requireNonNull(gatewayRefundId, "gatewayRefundId must not be null");
        return execute(() ->
                jdbcTemplate.query(FIND_BY_GATEWAY_REFUND_ID, rowMapper, gatewayRefundId)
                        .stream().findFirst());
    }

    public List<RefundEntity> findByGatewayPaymentId(UUID gatewayPaymentId) {
        return execute(() ->
                jdbcTemplate.query(FIND_BY_GATEWAY_PAYMENT, rowMapper, gatewayPaymentId));
    }

    public List<RefundEntity> findPending(int limit) {
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        return execute(() -> jdbcTemplate.query(FIND_PENDING, rowMapper, limit));
    }

    private void bindInsert(PreparedStatement statement, RefundEntity entity)
            throws SQLException {
        int index = 1;
        statement.setObject(index++, entity.id());
        statement.setObject(index++, entity.gatewayPaymentId());
        statement.setString(index++, entity.gatewayRefundId());
        statement.setLong(index++, entity.amount());
        statement.setString(index++, entity.currency());
        statement.setString(index++, entity.status());
        statement.setString(index++, entity.speedRequested());
        statement.setString(index++, entity.speedProcessed());
        statement.setBoolean(index++, entity.autoRefund());
        JdbcSupport.bind(statement, index++, entity.notes());
        statement.setString(index++, entity.acquirerReferenceNumber());
        statement.setString(index++, entity.batchId());
        statement.setObject(index++, entity.createdAt());
        statement.setObject(index++, entity.updatedAt());
        statement.setLong(index, entity.version());
    }

    private RefundEntity mapRow(java.sql.ResultSet rs, int rowNumber) throws SQLException {
        return new RefundEntity(
                JdbcSupport.readUuid(rs, "id"),
                JdbcSupport.readUuid(rs, "gateway_payment_id"),
                rs.getString("gateway_refund_id"),
                rs.getLong("amount"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getString("speed_requested"),
                rs.getString("speed_processed"),
                rs.getBoolean("auto_refund"),
                JdbcSupport.readJsonb(rs, "notes"),
                rs.getString("acquirer_reference_number"),
                rs.getString("batch_id"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getLong("version"));
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException exception) {
            throw PaymentPersistenceExceptionTranslator.translate(exception);
        }
    }
}
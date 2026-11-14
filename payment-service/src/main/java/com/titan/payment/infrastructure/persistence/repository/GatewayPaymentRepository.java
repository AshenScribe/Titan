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

import com.titan.payment.domain.entity.enums.GatewayPaymentStatus;
import com.titan.payment.infrastructure.persistence.model.GatewayPaymentEntity;
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
public class GatewayPaymentRepository {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String COLUMNS =
            "id, payment_attempt_id, gateway_payment_id, amount, amount_refunded, "
                    + "currency, status, method, captured, notes, gateway_created_at, "
                    + "created_at, updated_at, version";

    private static final String TABLE_NAME = "gateway_payments";

    private static final String INSERT_STATEMENT =
            """
            INSERT INTO %s (
                id, payment_attempt_id, gateway_payment_id, amount, amount_refunded,
                currency, status, method, captured, notes, gateway_created_at,
                created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(TABLE_NAME);

    private static final String FIND_BY_ID =
            "SELECT %s FROM %s WHERE id = ?".formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_GATEWAY_ID =
            "SELECT %s FROM %s WHERE gateway_payment_id = ?".formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_ATTEMPT =
            """
            SELECT %s FROM %s WHERE payment_attempt_id = ?
            ORDER BY created_at DESC, id DESC
            """.formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_STATUS_UPDATED_BEFORE =
            """
            SELECT %s FROM %s
            WHERE status = ? AND updated_at < ?
            ORDER BY updated_at
            LIMIT ?
            """.formatted(COLUMNS, TABLE_NAME);

    private static final String UPDATE =
            """
            UPDATE %s
            SET amount             = ?,
                amount_refunded    = ?,
                status             = ?,
                method             = ?,
                captured           = ?,
                notes              = ?,
                gateway_created_at = ?,
                version            = version + 1,
                updated_at         = ?
            WHERE id = ? AND version = ?
            """.formatted(TABLE_NAME);

    private final RowMapper<GatewayPaymentEntity> rowMapper = this::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public GatewayPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(GatewayPaymentEntity entity) {
        execute(() -> jdbcTemplate.update(
                INSERT_STATEMENT,
                statement -> bindInsert(statement, entity)));
    }

    public int update(GatewayPaymentEntity entity) {
        return execute(() -> jdbcTemplate.update(
                UPDATE,
                entity.amount(),
                entity.amountRefunded(),
                entity.status(),
                entity.method(),
                entity.captured(),
                JdbcSupport.asParam(entity.notes()),
                entity.gatewayCreatedAt(),
                entity.updatedAt(),
                entity.id(),
                entity.version()));
    }

    public Optional<GatewayPaymentEntity> findById(UUID id) {
        return execute(() -> jdbcTemplate.query(FIND_BY_ID, rowMapper, id).stream().findFirst());
    }

    public Optional<GatewayPaymentEntity> findByGatewayPaymentId(String gatewayPaymentId) {
        Objects.requireNonNull(gatewayPaymentId, "gatewayPaymentId must not be null");
        return execute(() ->
                jdbcTemplate.query(FIND_BY_GATEWAY_ID, rowMapper, gatewayPaymentId)
                        .stream().findFirst());
    }

    public List<GatewayPaymentEntity> findByPaymentAttemptId(UUID attemptId) {
        return execute(() -> jdbcTemplate.query(FIND_BY_ATTEMPT, rowMapper, attemptId));
    }

    public List<GatewayPaymentEntity> findByStatusUpdatedBefore(
            GatewayPaymentStatus status, OffsetDateTime before, int limit) {
        Objects.requireNonNull(status, "Status must not be null");
        Objects.requireNonNull(before, "Cutoff must not be null");
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        return execute(() ->
                jdbcTemplate.query(FIND_BY_STATUS_UPDATED_BEFORE, rowMapper,
                        status.name(), before, limit));
    }

    private void bindInsert(PreparedStatement statement, GatewayPaymentEntity entity)
            throws SQLException {
        int index = 1;
        statement.setObject(index++, entity.id());
        statement.setObject(index++, entity.paymentAttemptId());
        statement.setString(index++, entity.gatewayPaymentId());
        statement.setLong(index++, entity.amount());
        statement.setLong(index++, entity.amountRefunded());
        statement.setString(index++, entity.currency());
        statement.setString(index++, entity.status());
        statement.setString(index++, entity.method());
        statement.setBoolean(index++, entity.captured());
        JdbcSupport.bind(statement, index++, entity.notes());
        statement.setObject(index++, entity.gatewayCreatedAt());
        statement.setObject(index++, entity.createdAt());
        statement.setObject(index++, entity.updatedAt());
        statement.setLong(index, entity.version());
    }

    private GatewayPaymentEntity mapRow(java.sql.ResultSet rs, int rowNumber) throws SQLException {
        return new GatewayPaymentEntity(
                JdbcSupport.readUuid(rs, "id"),
                JdbcSupport.readUuid(rs, "payment_attempt_id"),
                rs.getString("gateway_payment_id"),
                rs.getLong("amount"),
                rs.getLong("amount_refunded"),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getString("method"),
                rs.getBoolean("captured"),
                JdbcSupport.readJsonb(rs, "notes"),
                rs.getObject("gateway_created_at", OffsetDateTime.class),
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
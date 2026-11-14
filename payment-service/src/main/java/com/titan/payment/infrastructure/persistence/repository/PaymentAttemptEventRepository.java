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

import com.titan.payment.infrastructure.persistence.model.PaymentAttemptEventEntity;
import com.titan.payment.infrastructure.persistence.util.JdbcSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
public class PaymentAttemptEventRepository {

    private static final String COLUMNS =
            "id, payment_attempt_id, from_status, to_status, source, "
                    + "webhook_event_id, metadata, created_at";

    private static final String TABLE_NAME = "payment_attempt_events";

    private static final String INSERT_STATEMENT =
            """
            INSERT INTO %s (
                id, payment_attempt_id, from_status, to_status, source,
                webhook_event_id, metadata, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(TABLE_NAME);

    private static final String FIND_BY_ATTEMPT =
            """
            SELECT %s FROM %s WHERE payment_attempt_id = ? ORDER BY created_at, id
            """.formatted(COLUMNS, TABLE_NAME);

    private final RowMapper<PaymentAttemptEventEntity> rowMapper = this::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public PaymentAttemptEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(PaymentAttemptEventEntity entity) {
        execute(() -> jdbcTemplate.update(
                INSERT_STATEMENT,
                statement -> bindInsert(statement, entity)));
    }

    public List<PaymentAttemptEventEntity> findByPaymentAttemptId(UUID paymentAttemptId) {
        return execute(() -> jdbcTemplate.query(FIND_BY_ATTEMPT, rowMapper, paymentAttemptId));
    }

    private void bindInsert(PreparedStatement statement, PaymentAttemptEventEntity entity)
            throws SQLException {
        int index = 1;
        statement.setObject(index++, entity.id());
        statement.setObject(index++, entity.paymentAttemptId());
        statement.setString(index++, entity.fromStatus());
        statement.setString(index++, entity.toStatus());
        statement.setString(index++, entity.source());
        statement.setString(index++, entity.webhookEventId());
        JdbcSupport.bind(statement, index++, entity.metadata());
        statement.setObject(index, entity.createdAt());
    }

    private PaymentAttemptEventEntity mapRow(java.sql.ResultSet rs, int rowNumber)
            throws SQLException {
        return new PaymentAttemptEventEntity(
                JdbcSupport.readUuid(rs, "id"),
                JdbcSupport.readUuid(rs, "payment_attempt_id"),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("source"),
                rs.getString("webhook_event_id"),
                JdbcSupport.readJsonb(rs, "metadata"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException exception) {
            throw PaymentPersistenceExceptionTranslator.translate(exception);
        }
    }
}
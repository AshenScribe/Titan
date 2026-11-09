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

package com.titan.product.orderservice.data.repository;

import com.titan.product.orderservice.data.entity.OrderIdempotencyEntity;
import com.titan.product.orderservice.data.exception.OrderPersistenceExceptionTranslator;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderIdempotencyRepository {

    private static final String COLUMNS =
            """
                    user_id, idempotency_key, request_hash, order_id, created_at, expires_at
                    """;

    private static final String TABLE_NAME = "order_idempotency";

    private static final String INSERT_OR_REPLACE_EXPIRED =
            """
            INSERT INTO %s (
                user_id, idempotency_key, request_hash, order_id, created_at, expires_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?
            )
                    ON CONFLICT (user_id, idempotency_key)
                    DO UPDATE SET
                        request_hash = EXCLUDED.request_hash,
                        order_id = EXCLUDED.order_id,
                        created_at = EXCLUDED.created_at,
                        expires_at = EXCLUDED.expires_at
                    WHERE %s.expires_at <= EXCLUDED.created_at
                    """
                    .formatted(TABLE_NAME, TABLE_NAME);

    private static final String FIND_BY_KEY =
            """
                    SELECT %s
                    FROM %s
                    WHERE user_id = ?
                      AND idempotency_key = ?
                      AND expires_at > ?
                    """
                    .formatted(COLUMNS, TABLE_NAME);

    private static final String DELETE_EXPIRED =
            """
                    DELETE FROM %s
                    WHERE expires_at <= ?
                    """
                    .formatted(TABLE_NAME);

    private final RowMapper<OrderIdempotencyEntity> idempotencyMapper = this::mapIdempotency;

    private final JdbcTemplate jdbcTemplate;

    public OrderIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts a new idempotency record or replaces an expired record.
     *
     * <p>An active record with the same user ID and idempotency key is never overwritten.
     *
     * @param entity the idempotency record
     * @return true if inserted or replaced, false if an active record already exists
     */
    public boolean insertOrReplaceExpired(OrderIdempotencyEntity entity) {
        try {
            return jdbcTemplate.update(
                            INSERT_OR_REPLACE_EXPIRED, statement -> bindFields(statement, entity))
                    == 1;
        } catch (DataAccessException e) {
            throw OrderPersistenceExceptionTranslator.translate(e);
        }
    }

    /**
     * Finds an active idempotency record.
     *
     * <p>Expired records are treated as absent.
     *
     * @param userId the user ID
     * @param idempotencyKey the idempotency key
     * @param now the current timestamp
     * @return the active record, or empty if absent or expired
     */
    public Optional<OrderIdempotencyEntity> findByKey(
            UUID userId, String idempotencyKey, OffsetDateTime now) {

        List<OrderIdempotencyEntity> results =
                jdbcTemplate.query(FIND_BY_KEY, idempotencyMapper, userId, idempotencyKey, now);

        return results.stream().findFirst();
    }

    /**
     * Deletes all expired idempotency records.
     *
     * @param now the current timestamp
     * @return the number of deleted records
     */
    public int deleteExpired(OffsetDateTime now) {
        return jdbcTemplate.update(DELETE_EXPIRED, now);
    }

    private void bindFields(PreparedStatement statement, OrderIdempotencyEntity entity)
            throws SQLException {

        statement.setObject(1, entity.userId());
        statement.setString(2, entity.idempotencyKey());
        statement.setBytes(3, entity.requestHash());
        statement.setObject(4, entity.orderId());
        statement.setObject(5, entity.createdAt());
        statement.setObject(6, entity.expiresAt());
    }

    private OrderIdempotencyEntity mapIdempotency(ResultSet resultSet, int rowNumber)
            throws SQLException {

        return new OrderIdempotencyEntity(
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("idempotency_key"),
                resultSet.getBytes("request_hash"),
                resultSet.getObject("order_id", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("expires_at", OffsetDateTime.class));
    }
}
